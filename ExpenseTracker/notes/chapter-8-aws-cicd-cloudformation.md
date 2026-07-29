# Chapter 8 — CI/CD to AWS (GitHub Actions, IAM, CloudFormation)

> **Scope:** How this project stops being "a Compose file on a laptop" and
> becomes infrastructure that AWS creates for us, on every push to `main`.
>
> Chapter 7 packaged the services into images and put Kong in front of them, all
> running on one machine. This chapter is about the *other* machine — the one in
> `ap-south-1` that doesn't exist yet. We describe it in a YAML file, hand that
> file to CloudFormation, and let a GitHub Actions runner do the handing.
>
> Nothing here deploys the application yet. This chapter builds the **empty
> house**: VPC, subnets, gateways, load balancer, ECS cluster. Moving in comes
> later.
>
> §7 maps every concept onto the exact files in this repo.

**Table of contents**

1. [What changes from chapter 7](#1-what-changes-from-chapter-7)
2. [The credentials — IAM user, access keys, GitHub secrets](#2-the-credentials--iam-user-access-keys-github-secrets)
3. [GitHub Actions anatomy](#3-github-actions-anatomy)
4. [Infrastructure as Code — CloudFormation](#4-infrastructure-as-code--cloudformation)
5. [Reading the template we deploy](#5-reading-the-template-we-deploy)
6. [The outputs → S3 handshake](#6-the-outputs--s3-handshake)
7. [Where this lives in the codebase](#7-where-this-lives-in-the-codebase)
8. [Bugs in the reference script, and why they matter](#8-bugs-in-the-reference-script-and-why-they-matter)
9. [Gotchas and cost](#9-gotchas-and-cost)
10. [Quick revision sheet](#10-quick-revision-sheet)

---

## 1. What changes from chapter 7

In chapter 7 the deployment story was: clone the repo, run
`docker compose up`. Every dependency was a container on one host, and the
"network" was a Docker bridge network that Compose created for us.

On AWS none of that is free. There is no implicit bridge network, no
`localhost`, no host that already exists. Every single thing has to be asked
for — and the interesting question becomes *who asks, and how*.

Three bad answers, and the good one:

| Approach | Problem |
|---|---|
| Click through the AWS Console | Not reproducible. Nobody can tell what you clicked, or recreate it in another region. |
| A `deploy.sh` full of `aws ec2 create-*` calls | Not idempotent. Running it twice creates two VPCs. Failing halfway leaves debris. |
| Run it from your laptop | The deploy depends on *your* machine, *your* credentials, *your* AWS CLI version. |
| **Declare the desired state in a file; let a CI runner apply it** | ✅ |

That last line is the whole chapter. Two halves:

- **CloudFormation** — a YAML file describing *what should exist*. Not steps;
  a destination. AWS works out the steps.
- **GitHub Actions** — a runner that watches the repo and, on every push to
  `main`, hands that file to CloudFormation.

```
   git push main
        │
        ▼
   GitHub Actions runner (a throwaway Ubuntu VM)
        │  reads the repo
        │  assumes AWS identity via secrets
        ▼
   aws cloudformation deploy  ──►  CloudFormation  ──►  creates/updates
                                                        VPC, subnets, IGW,
                                                        NAT, ALB, ECS cluster
```

---

## 2. The credentials — IAM user, access keys, GitHub secrets

### 2.1 What an access key actually is

An IAM user has a **name/password** for the Console (a human logging in) and,
separately, an **access key pair** for the API:

```
AWS_ACCESS_KEY_ID       AKIA................     ← public-ish identifier
AWS_SECRET_ACCESS_KEY   wJalrXUtn................ ← the actual secret
```

The AWS CLI signs every request with these. They are a *username and password
for the API* — and critically, **they never expire**. If one leaks, it works
until somebody notices and revokes it. Bots scrape GitHub for `AKIA` strings
within minutes of a push.

### 2.2 Where they go

Repo → **Settings → Secrets and variables → Actions → New repository secret**:

| Name | Value |
|---|---|
| `AWS_ACCESS_KEY_ID` | the `AKIA...` string |
| `AWS_SECRET_ACCESS_KEY` | the secret string |

GitHub encrypts these at rest and injects them only into workflow runs. They are
write-only in the UI — you cannot read them back, only replace them. Anything a
workflow prints is scrubbed if it matches a secret value.

> **Never** put these in the repo, in `.env`, in `application.properties`, or in
> the CloudFormation template. The `.gitignore` already excludes `.env`; that is
> a safety net, not a strategy.

### 2.3 The key insight: the runner has no power of its own

> GitHub Actions can read the repo, and it can *call* AWS.
> It cannot do anything inside AWS that the IAM user isn't allowed to do.

This is worth sitting with, because it's the security model in one line. The
runner is just a machine holding a credential. Every `aws` command it runs is
evaluated against the IAM user's **policies**. If that user has no
`ec2:CreateVpc` permission, the deploy fails with `AccessDenied` no matter what
the workflow file says.

So the blast radius of a leaked key is exactly the IAM user's permissions —
which is the argument for **least privilege**: give the deploy user only what
the template needs, not `AdministratorAccess`.

### 2.4 The better answer (for later)

Long-lived keys are the beginner-friendly option, not the correct one. In
production, GitHub Actions uses **OIDC**: GitHub presents a short-lived signed
token proving "this is repo X, branch `main`", AWS trusts GitHub as an identity
provider, and hands back credentials valid for ~1 hour.

```
   long-lived keys        OIDC federation
   ─────────────────      ────────────────────────────────────
   stored in GitHub       nothing stored
   never expire           expires in ~1 hour
   leak = permanent       leak = worthless in an hour
   scoped to IAM user     scoped to repo *and* branch
```

Same workflow file, minus the two `secrets.*` lines, plus a `role-to-assume`.
Worth migrating to once the pipeline works.

---

## 3. GitHub Actions anatomy

A workflow is a YAML file that GitHub reads from **`.github/workflows/`** at the
**repository root**. Four nested concepts:

```
workflow  (the file)
  └── job         (runs on one fresh VM; jobs are parallel by default)
        └── step  (one action or one shell command; steps are sequential)
```

### 3.1 The trigger

```yaml
on:
  push:
    branches: [main]
    paths:
      - 'ExpenseTracker/cloudformation-template--initial-infra.yaml'
      - '.github/workflows/deploy-infrastructure.yml'
  workflow_dispatch:
```

`paths` is the important part. Without it, every push — a fixed typo in a note,
a new service — triggers a full infrastructure deploy. With it, the workflow
only fires when the infrastructure description itself changes.

`workflow_dispatch` adds a **Run workflow** button in the Actions tab, so you can
deploy without a dummy commit.

### 3.2 `uses:` vs `run:`

Two kinds of step, and the distinction matters:

| | What it is | Example |
|---|---|---|
| `uses:` | A reusable action from the marketplace — someone else's packaged code | `actions/checkout@v4` |
| `run:` | A shell command on the runner | `aws cloudformation deploy ...` |

`@v4` is a version tag. **Pin it and keep it current** — actions on old tags run
on end-of-life Node versions and eventually stop working entirely (see §8).

Three actions do all the setup here:

- **`actions/checkout@v4`** — clones the repo onto the runner. Without this the
  working directory is *empty*; the template file simply isn't there. Easy to
  forget, obvious once it fails.
- **`aws-actions/configure-aws-credentials@v4`** — takes the secrets and writes
  them where the AWS CLI looks (env vars `AWS_ACCESS_KEY_ID`,
  `AWS_SECRET_ACCESS_KEY`, `AWS_DEFAULT_REGION`). Every later `aws` command is
  authenticated because of this one step. The AWS CLI itself is
  **pre-installed** on `ubuntu-latest` — no install step needed.
- **`actions/upload-artifact@v4`** — attaches a file to the run so you can
  download it from the Actions page.

### 3.3 `env`, `concurrency`, `working-directory`

```yaml
env:
  AWS_REGION: ap-south-1
  STACK_NAME: expense-tracker-infrastructure-stack
```

Workflow-level `env` is available to every step as `${{ env.X }}` (in YAML) or
`$X` (inside a shell script). Putting every constant here means changing the
region is a one-line edit, not a find-and-replace.

```yaml
concurrency:
  group: expense-tracker-infrastructure
  cancel-in-progress: false
```

Two quick pushes would otherwise start two deploys. The second would hit
`UPDATE_IN_PROGRESS` and fail. `concurrency` makes the second run *wait*.
`cancel-in-progress: false` matters here — killing a half-finished stack update
is worse than queueing.

```yaml
defaults:
  run:
    working-directory: ExpenseTracker
```

Every `run:` step executes inside `ExpenseTracker/`, so paths are relative to the
project rather than the repo root. See §7 for why this exists.

---

## 4. Infrastructure as Code — CloudFormation

### 4.1 Declarative, not imperative

A shell script says *do this, then this*. A CloudFormation template says *this
should exist*. The difference shows up on the second run:

```
   script:    run twice  →  two VPCs
   template:  run twice  →  "No changes to deploy"
```

CloudFormation keeps a **stack** — a record of everything it created from that
template. On the next deploy it diffs the new template against the current stack
and computes a **ChangeSet**: create these, update those, delete the rest.

### 4.2 Rollback

If resource 14 of 20 fails, CloudFormation **deletes the 13 it already made** and
returns the stack to its previous state. A failed deploy leaves nothing behind.
That property is why this beats a script — a script that dies halfway leaves you
to clean up by hand, and you will miss something (usually an Elastic IP, which
then bills you monthly for nothing).

### 4.3 Template sections

```yaml
AWSTemplateFormatVersion: '2010-09-09'   # fixed string, always this value
Description: ...                          # shows in the Console
Mappings:                                 # static lookup tables
Resources:                                # ← the only required section
Outputs:                                  # values to publish
```

Also available but unused here: `Parameters` (inputs at deploy time),
`Conditions` (create X only if Y).

### 4.4 Intrinsic functions

These are the vocabulary. All five appear in our template:

| Function | Does | Example |
|---|---|---|
| `!Ref` | The "main" identity of a resource — usually its ID | `!Ref VPC` → `vpc-0a1b2c` |
| `!GetAtt` | A *specific attribute* of a resource | `!GetAtt PublicLoadBalancer.DNSName` |
| `!Sub` | String interpolation | `!Sub '${AWS::StackName}-vpc'` |
| `!FindInMap` | Read from `Mappings` | `!FindInMap [SubnetConfig, VPC, CIDR]` |
| `!Select` + `!GetAZs` | Pick the Nth AZ of the current region | `!Select [0, !GetAZs '']` |

`!Ref` and `!GetAtt` do the real work: they create the **dependency graph**.
When `PublicRoute` says `GatewayId: !Ref InternetGateway`, CloudFormation knows
the gateway must be built first. You never order the resources yourself — you
just reference them, and the order falls out. `DependsOn:` is only for the rare
case where a dependency exists but isn't expressed by a reference (our NAT
Elastic IPs need the gateway *attached*, which no `!Ref` captures).

`!GetAZs` deserves a note: it makes the template **region-portable**. `!Select
[0, ...]` means "the first AZ here", so the same file works in `ap-south-1` and
`us-east-1` without edits. Hardcoding `ap-south-1a` would break that.

### 4.5 `deploy` vs `create-stack`

```bash
aws cloudformation deploy \
  --template-file cloudformation-template--initial-infra.yaml \
  --stack-name expense-tracker-infrastructure-stack \
  --capabilities CAPABILITY_NAMED_IAM \
  --no-fail-on-empty-changeset
```

`deploy` is create-or-update in one command, and it **waits** for completion
rather than returning immediately — which is what you want in CI, where the next
step depends on the stack being finished.

- **`--capabilities CAPABILITY_NAMED_IAM`** — an explicit acknowledgement.
  Templates that create IAM roles can escalate privilege, so AWS refuses unless
  you say "yes, I know this template touches IAM." `NAMED_IAM` is the variant
  required when the roles have explicit names.
- **`--no-fail-on-empty-changeset`** — without it, pushing an unrelated commit
  produces a red ❌ build for the crime of nothing having changed.

---

## 5. Reading the template we deploy

This is the standard AWS ECS reference network. It's worth reading top-to-bottom
once, because everything in learning notes 14 and 16 appears here as literal
YAML.

### 5.1 The address plan

```yaml
Mappings:
  SubnetConfig:
    VPC:        { CIDR: '10.0.0.0/16' }
    PublicOne:  { CIDR: '10.0.0.0/24' }
    PublicTwo:  { CIDR: '10.0.1.0/24' }
    PrivateOne: { CIDR: '10.0.2.0/24' }
    PrivateTwo: { CIDR: '10.0.3.0/24' }
```

Every CIDR in one place. Change the plan here, not in five separate resources.

```
   VPC 10.0.0.0/16  (65,536 addresses)
   │
   ├── AZ ap-south-1a ────────────────────┐
   │     Public  10.0.0.0/24   IGW route  │  ALB node, NAT GW 1
   │     Private 10.0.2.0/24   NAT route  │  containers
   │                                      │
   └── AZ ap-south-1b ────────────────────┤
         Public  10.0.1.0/24   IGW route  │  ALB node, NAT GW 2
         Private 10.0.3.0/24   NAT route  │  containers
```

Two AZs because an ALB **requires** at least two subnets in different AZs — and
because one AZ failing shouldn't take the system down.

### 5.2 What makes a subnet "public"

Nothing about the subnet itself. A subnet is public **only** because its route
table has `0.0.0.0/0 → Internet Gateway`:

```yaml
PublicRoute:
  Properties:
    RouteTableId: !Ref PublicRouteTable
    DestinationCidrBlock: '0.0.0.0/0'
    GatewayId: !Ref InternetGateway
```

`MapPublicIpOnLaunch: true` on the public subnets is the second half: anything
started there gets a public IP automatically. The private subnets omit it.

### 5.3 The NAT pair

```yaml
NatGatewayOne:  { SubnetId: !Ref PublicSubnetOne }   # + its own Elastic IP
NatGatewayTwo:  { SubnetId: !Ref PublicSubnetTwo }
```

and **two separate private route tables**, one per AZ, each pointing at the NAT
in its own AZ:

```
   PrivateRouteTableOne  →  0.0.0.0/0 → NatGatewayOne   (attached to PrivateSubnetOne)
   PrivateRouteTableTwo  →  0.0.0.0/0 → NatGatewayTwo   (attached to PrivateSubnetTwo)
```

This is the correct HA pattern, and the fix for the single-NAT weakness noted in
learning note 16 §6. One NAT shared by both AZs means AZ-2 failing kills AZ-1's
outbound Internet too, and every cross-AZ byte is billed twice (transfer *plus*
NAT processing).

Note the NAT sits in a **public** subnet. It has to: the NAT rewrites the source
address, then still needs its own route to the IGW to actually leave. Two hops.

### 5.4 The load balancer

```yaml
PublicLoadBalancer:
  Properties:
    Scheme: internet-facing
    Subnets: [!Ref PublicSubnetOne, !Ref PublicSubnetTwo]
    SecurityGroups: [!Ref PublicLoadBalancerSG]
```

`internet-facing` (vs `internal`) is what gives the ALB nodes public IPs.

The **dummy target group** is the interesting bit:

```yaml
DummyTargetGroupPublic:      # points at nothing
PublicLoadBalancerListener:  # forwards port 80 → the dummy
```

An ALB listener *must* have a default action, but there are no services yet.
So the listener forwards to an empty target group and every request gets a 503.
The dummy is a placeholder that lets this stack stand alone; later service
stacks add their own target groups and listener rules alongside it.

### 5.5 The two security groups

This is the chapter-7 "Kong is the one door" idea, enforced by the network
instead of by convention:

```yaml
PublicLoadBalancerSG:            # from the whole Internet
  SecurityGroupIngress:
    - CidrIp: 0.0.0.0/0
      IpProtocol: -1

ContainerSecurityGroup:          # from the load balancer ONLY
  SecurityGroupIngress:
    - IpProtocol: tcp
      FromPort: 0
      ToPort: 65535
      SourceSecurityGroupId: !Ref PublicLoadBalancerSG   ← not a CIDR
```

`SourceSecurityGroupId` is the pattern to remember. The container rule doesn't
name an IP range — it names *the load balancer's security group*. ALB nodes
scale up and down and their IPs change constantly; referencing the SG means the
rule keeps working regardless.

A security group is **not a box sitting between** the ALB and the containers. It
attaches to each resource's network interface and is evaluated *there* — two
checks, one at each end. And it is **stateful**: allow the request in, and the
reply is allowed out automatically. No return rule needed.

> ⚠️ `PublicLoadBalancerSG` allows `IpProtocol: -1` — *all* ports, not just 80.
> The ALB only listens on 80 so nothing is exposed in practice, but the rule is
> wider than the intent. Tighten to `tcp/80` (and `443` once TLS exists).

### 5.6 Outputs

```yaml
Outputs:
  VpcId:          { Value: !Ref VPC }
  PrivateSubnetOne: { Value: !Ref PrivateSubnetOne }
  ECSCluster:     { Value: !Ref ECSCluster }
  ExternalUrl:    { Value: !Sub 'http://${PublicLoadBalancer.DNSName}' }
```

Resource IDs are generated by AWS, so they can't be known in advance. `Outputs`
is how the stack publishes them. `ExternalUrl` is the one to grab after the
first deploy — it's the public address of the system.

---

## 6. The outputs → S3 handshake

The last two workflow steps look odd until you see what they're for:

```bash
aws cloudformation describe-stacks \
  --stack-name expense-tracker-infrastructure-stack \
  --query "Stacks[0].Outputs" \
  > infrastructure-outputs.json

aws s3 cp infrastructure-outputs.json s3://$BUCKET_NAME/infrastructure-outputs.json
```

**The problem being solved:** infrastructure and application are deliberately
*separate* stacks. The network changes rarely; the app deploys constantly.
Merging them would mean every code push risks touching the VPC.

But the application stack needs to know the subnet IDs, the cluster name, the
load balancer's SG. Those live in stack 1. So stack 1 writes them to a known S3
location, and stack 2 reads them:

```
   infra stack  ──deploy──►  Outputs  ──►  S3: infrastructure-outputs.json
                                                       │
   service stack  ◄────────── reads ───────────────────┘
```

`--query` is **JMESPath**, evaluated client-side by the AWS CLI.
`Stacks[0].Outputs` pulls the outputs array out of the response envelope.

> CloudFormation has a native mechanism for this — `Export` on an output plus
> `!ImportValue` in the consuming stack. It's stricter (an exported value cannot
> be changed while something imports it), which is either a safety feature or an
> obstacle depending on how often the network churns. The S3 file is the looser,
> more common CI pattern.

**S3 buckets are globally unique across all AWS accounts.** `expensetrackerinfra`
may well be taken by a stranger; `aws s3 mb` then fails with
`BucketAlreadyExists`. Suffix it with something personal.

---

## 7. Where this lives in the codebase

```
D:\POC\Jassi\                                       ← git repo root
├── .github\workflows\
│   └── deploy-infrastructure.yml                   ← the workflow
└── ExpenseTracker\                                 ← project root
    ├── cloudformation-template--initial-infra.yaml ← the template
    ├── docker-compose.yml                          (chapter 7)
    └── notes\
```

### The one thing that surprises everyone

The template belongs to the project, so it sits in `ExpenseTracker/`. The
workflow does **not** — because:

> GitHub Actions only reads workflows from `.github/workflows/` at the **git
> repository root**. Nowhere else. A workflow at
> `ExpenseTracker/.github/workflows/` is an inert file GitHub never looks at.

Our repo root is `D:\POC\Jassi`, with ExpenseTracker as a subfolder. So the
workflow lives at the root and is *scoped* to the project two ways:

```yaml
defaults:
  run:
    working-directory: ExpenseTracker    # every run: step starts here

on:
  push:
    paths:
      - 'ExpenseTracker/cloudformation-template--initial-infra.yaml'
```

With `working-directory` set, `--template-file cloudformation-template--initial-infra.yaml`
resolves exactly as if ExpenseTracker were the root.

Two things it does **not** affect, which is a classic source of confusion:

- **`uses:` steps ignore it.** `actions/upload-artifact` needs the full path
  from the repo root: `ExpenseTracker/infrastructure-outputs.json`.
- **`actions/checkout` still clones to the repo root**, not into
  `ExpenseTracker/`.

If ExpenseTracker later becomes its own GitHub repository, move `.github/` down
into it and delete the `defaults:` block. Nothing else changes.

---

## 8. Bugs in the reference script, and why they matter

The starting reference had four defects. All four are the kind that cost an
afternoon, so they're worth naming.

| Written | What happens | Correct |
|---|---|---|
| `aws s3api head-bucket --bucket-name X` | No such flag. The command *always* errors, so the `if !` always takes the create branch, so `mb` always runs and fails on the second deploy. | `--bucket X` |
| `--template-file X.yaml\` | No space before the `\`. The shell joins the lines, and the filename becomes `X.yaml--stack-name`. `File does not exist`. | space before `\` |
| `actions/checkout@v2`, `configure-aws-credentials@v1` | Node 12/16 actions. GitHub has retired those runtimes; these now warn and eventually fail outright. | `@v4` on both |
| `aws cloudformation deploy` (bare) | Exits non-zero on an empty changeset. An unrelated commit turns the build red. | add `--no-fail-on-empty-changeset` |

The `head-bucket` one is the instructive failure: the command was *silently*
wrong in a way that only shows up on the **second** run. First deploy — bucket
doesn't exist, create it, works. Second deploy — bucket exists, but the broken
check reports "missing" anyway, `mb` runs, `BucketAlreadyOwnedByYou`, red build.

Also worth adding, and now present:

- **`aws cloudformation validate-template`** as a step before `deploy` — catches
  YAML and schema errors in two seconds instead of after a five-minute rollback.
- **`upload-artifact`** for the outputs JSON — inspect it from the Actions page
  without opening the S3 console.

---

## 9. Gotchas and cost

**NAT Gateways are the expensive part.** Two of them, in `ap-south-1`, is roughly
**$65–70/month** *before* a single byte of data. They bill hourly whether traffic
flows or not. This dwarfs everything else in the stack — the VPC, subnets, route
tables, IGW and security groups are all free.

```bash
aws cloudformation delete-stack --stack-name expense-tracker-infrastructure-stack
```

Run that when you stop experimenting for the day. Because everything was created
by the stack, one command removes all of it — no orphaned Elastic IPs quietly
billing you.

Other traps:

- **Elastic IPs bill when unattached.** Deleting a NAT by hand but leaving its
  EIP costs money for nothing. Another argument for deleting the *stack*.
- **NAT Gateways take ~2 minutes each to create**, and the same to delete. First
  deploy is 3–5 minutes; be patient before assuming it hung.
- **A stack in `ROLLBACK_COMPLETE` cannot be updated.** If the very first create
  fails, you must `delete-stack` before retrying — `deploy` will not recover it.
  This only affects the initial create; later failures roll back cleanly.
- **`ClusterName: 'expense-tracker'` is hardcoded**, so this template can't be
  deployed twice in one region. Fine for now; `!Sub '${AWS::StackName}-cluster'`
  is the fix when a staging environment appears.
- **Region matters for the whole stack.** `ap-south-1` here. Deploying to a
  different region creates an entirely separate copy of everything — stacks do
  not span regions.
- **Nothing in this template runs the application.** The ALB returns 503 by
  design until a service stack registers real targets.

---

## 10. Quick revision sheet

- **CI/CD split:** CloudFormation says *what should exist*; GitHub Actions is
  just the thing that hands the file over.
- Access keys are an **API username/password that never expire**. They live in
  GitHub repo secrets and nowhere else.
- **The runner has no power of its own** — every action is checked against the
  IAM user's policies. Blast radius = that user's permissions. Least privilege.
- **OIDC** replaces long-lived keys with ~1-hour credentials scoped to repo and
  branch. The upgrade path.
- Workflows are read **only** from `.github/workflows/` at the **git repo root**.
- `uses:` = someone else's action, pinned by tag; `run:` = a shell command.
- **`actions/checkout` is not optional** — without it the runner has no files.
- `configure-aws-credentials` sets the env vars every later `aws` call relies on.
  The AWS CLI is pre-installed on `ubuntu-latest`.
- `defaults.run.working-directory` scopes `run:` steps only — **`uses:` steps
  still take repo-root paths**.
- `paths:` filters stop unrelated commits triggering infrastructure deploys.
- `concurrency` stops two deploys colliding on `UPDATE_IN_PROGRESS`.
- Declarative beats imperative: **run a template twice → "no changes"**; run a
  script twice → two VPCs.
- A failed deploy **rolls back** — the stack returns to its previous state,
  leaving no debris.
- `!Ref` = the resource's ID; `!GetAtt` = a named attribute. Together they build
  the dependency graph, so **you never order resources manually**.
- `!Select [0, !GetAZs '']` keeps the template region-portable.
- `--capabilities CAPABILITY_NAMED_IAM` = "I acknowledge this template touches
  IAM." `--no-fail-on-empty-changeset` = don't fail on a no-op.
- A subnet is **public only because of its route table** (`0.0.0.0/0 → IGW`).
  Nothing else marks it.
- **One NAT per AZ**, each with its own private route table. Sharing one NAT is a
  cross-AZ single point of failure *and* double data charges.
- The **NAT itself sits in a public subnet** — it rewrites the source address,
  then needs its own IGW route to get out. Two hops.
- An ALB needs **two subnets in two AZs**, minimum.
- The **dummy target group** exists because a listener must have a default
  action before any real service exists. Expect 503 until then.
- `SourceSecurityGroupId` **references another SG, not a CIDR** — ALB node IPs
  change; the reference doesn't.
- Security groups are **attached to interfaces, not placed between things**, and
  they are **stateful** — no return rule needed.
- `Outputs` publish AWS-generated IDs; the workflow copies them to S3 so the
  *next* stack can read them. (`Export`/`!ImportValue` is the stricter native
  alternative.)
- **S3 bucket names are globally unique across all AWS accounts.**
- **Two NAT Gateways ≈ $65–70/month, idle.** `delete-stack` when done.
- A first-create failure leaves `ROLLBACK_COMPLETE`, which **must be deleted**
  before retrying.

---

## References

- Chapter 7 — Docker & Kong (what gets deployed *into* this network)
- `learning/14-AWS-Region-VPC-AvailabilityZone-Subnets.md` — VPC, AZ, subnets, CIDR
- `learning/15-AWS-Services-EC2-RDS-DynamoDB-Docker.md` — EC2, RDS, scaling
- `learning/16-AWS-Complete-Flow-CICD-ELB-NAT.md` — the end-to-end request path
- `.github/workflows/deploy-infrastructure.yml` — the workflow
- `cloudformation-template--initial-infra.yaml` — the template
- AWS docs — CloudFormation template anatomy, intrinsic functions, `aws cloudformation deploy`
- GitHub docs — Workflow syntax, Encrypted secrets, OIDC for AWS
