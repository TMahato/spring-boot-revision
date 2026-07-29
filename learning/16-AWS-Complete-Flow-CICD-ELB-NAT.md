# AWS — The Complete Flow: GitHub → ECR → CloudFormation → ELB → EC2 → NAT

This note ties together [14 — Region/VPC/AZ/Subnets](./14-AWS-Region-VPC-AvailabilityZone-Subnets.md)
(the network) and [15 — EC2/RDS/DynamoDB/Docker](./15-AWS-Services-EC2-RDS-DynamoDB-Docker.md)
(the services) into **one end-to-end architecture**: how code gets deployed,
how a request from the Internet reaches it, and how it calls out again.

## 0. The whole picture

```
                    ┌──────────── DEPLOY PATH ────────────┐

  laptop ──push──► GitHub ──triggers──► GitHub Actions
                                              │
                                     1. IAM   — who am I, what may I do?
                                     2. ECR   — build image, push
                                     3. S3    — push configs / env files
                                     4. CFN   — deploy the stack
                                              │
  ═══════════════════════════════════════════════════════════════════════
                                              ▼
  Region: ap-south-1 (Mumbai)          VPC 10.10.0.0/16

        client ──► Route 53 ──► ELB DNS name ──► IGW
                                                  │
   ┌──────────────────────────┬───────────────────┼──────────────────────┐
   │  AZ-1                    │                   ▼      AZ-2            │
   │  ┌────────────────────┐  │            ┌───────────┐  ┌────────────┐ │
   │  │ Public Subnet 1    │  │            │    ELB    │  │ Public Sub2│ │
   │  │ 10.10.0.0/24       │  │            │  (nodes)  │  │10.10.1.0/24│ │
   │  │                    │  │            └─────┬─────┘  │  NAT GW    │ │
   │  └────────────────────┘  │                  │        └────────────┘ │
   │            │             │      VPC router  │              │        │
   │  ┌─────────▼──────────┐  │        (local)   ▼        ┌─────▼──────┐ │
   │  │ Private Subnet 1   │  │            ┌──────────┐   │Private Sub2│ │
   │  │ 10.10.2.0/24       │  │            │ EC2 +EBS │   │10.10.3.0/24│ │
   │  │  EC2 + EBS   [SG]  │  │            └──────────┘   │ EC2+EBS[SG]│ │
   │  └────────────────────┘  │                           └────────────┘ │
   └──────────────────────────┴────────────────────────────────────────  ┘
```

Two flows run through this: **deploy** (top) and **request** (bottom). They
share nothing except the artifacts in ECR and S3.

---

## 1. The deploy path (CI/CD)

```
laptop  ──push code──►  GitHub  ──.github/workflows/deploy.yml──►  runner
```

**Step 1 — IAM: check who is acting**

Before anything touches AWS, the pipeline authenticates. It assumes an **IAM
role** and gets **temporary credentials**. Every later call (push to ECR, write
to S3, create a stack) is authorised against that role's policy.

> Modern practice: use **GitHub OIDC → `sts:AssumeRole`**. GitHub presents a
> signed identity token, AWS trades it for short-lived credentials. **No
> long-lived access keys stored in GitHub secrets** — that's the classic leak.

**Step 2 — ECR: build and push the image**

```bash
docker build -t myapp:$GIT_SHA .
aws ecr get-login-password | docker login --username AWS --password-stdin <acct>.dkr.ecr.ap-south-1.amazonaws.com
docker push <acct>.dkr.ecr.ap-south-1.amazonaws.com/myapp:$GIT_SHA
```

**Tag with the commit SHA, not `latest`.** `latest` makes rollbacks ambiguous
and breaks the "which build is running?" question during an incident.

**Step 3 — S3: push configs and files**

Anything the container needs at startup but shouldn't be baked into the image:
config files, static assets, seed data, `.env`-style files.

- Baked into the image → immutable, but you rebuild for every config change.
- Fetched from S3 at boot → change config without rebuilding.
- **Secrets do not go here.** Passwords and API keys belong in **Secrets
  Manager** or **SSM Parameter Store**, read at runtime via the instance's IAM
  role.

**Step 4 — CloudFormation: deploy the stack**

CloudFormation is **Infrastructure as Code** — a template describing the VPC,
subnets, route tables, ELB, Auto Scaling Group, security groups, and it makes
reality match the template.

Parameters passed in (per the diagram):
- **which subnets** to launch into
- **env vars / which image tag** to run
- **S3 bucket URLs** for the configs

Why this matters: the whole environment is **reproducible and diffable**.
`ChangeSets` show you what will change *before* it changes, and a failed deploy
**rolls back automatically**. Nothing is clicked in the console.

---

## 2. The network layout

| | CIDR | AZ | Contains |
|---|------|----|----------|
| **VPC** | `10.10.0.0/16` | — | everything, spans both AZs |
| **Public Subnet 1** | `10.10.0.0/24` | AZ-1 | ELB node |
| **Public Subnet 2** | `10.10.1.0/24` | AZ-2 | ELB node, **NAT Gateway** |
| **Private Subnet 1** | `10.10.2.0/24` | AZ-1 | EC2 + EBS |
| **Private Subnet 2** | `10.10.3.0/24` | AZ-2 | EC2 + EBS |

Everything is duplicated across **two AZs** — that's the whole point. Lose AZ-1
and the ELB stops sending traffic there; AZ-2 serves everything.

### Route tables

Three route tables, and **which one a subnet points at is the only thing that
makes it public or private**:

```
┌─ PUBLIC RT ──────────────────────┐   attached to: Public Subnet 1, 2
│ 10.10.0.0/16  →  local           │
│ 0.0.0.0/0     →  igw-xxxxx       │   ← the door to the Internet
└──────────────────────────────────┘

┌─ PRIVATE RT ─────────────────────┐   attached to: Private Subnet 1, 2
│ 10.10.0.0/16  →  local           │
│ 0.0.0.0/0     →  nat-xxxxx       │   ← outbound only
└──────────────────────────────────┘

┌─ MAIN RT (default) ──────────────┐   attached to: nothing explicit
│ 10.10.0.0/16  →  local           │   ← no Internet route at all
└──────────────────────────────────┘
```

The **`local` route is in all three**, added automatically, cannot be deleted.
That single line is why the ELB can reach the EC2s across subnets *and* across
AZs with no configuration — and it's the key to §5 below.

The **VPC router** (drawn in the middle of the diagram) is the implicit device
that consults these tables. It's the `.1` address reserved in every subnet, and
it is your instances' default gateway.

---

## 3. Inbound flow — Internet to your app

```
1.  client types  https://myapp.com
        │
2.  DNS query ──► Route 53
        │         alias record:  myapp.com → myservice-elb-123456789.ap-south-1.elb.amazonaws.com
        │         resolves to the ELB nodes' public IPs
        ▼
3.  packet leaves client  →  AWS edge  →  IGW
        │                    IGW does 1:1 NAT: public IP → ELB node's private IP
        ▼
4.  ELB node  (public subnet, 10.10.0.x / 10.10.1.x)
        │      • TLS terminates here (ACM certificate)
        │      • picks a healthy target from the target group
        ▼
5.  VPC router  —  destination 10.10.2.15  matches  10.10.0.0/16 → local
        │           no IGW, no NAT, never leaves the VPC
        ▼
6.  EC2 in Private Subnet 1  (10.10.2.15:8080)
        │      • Security Group checks: inbound 8080 from ELB's SG? ✅
        ▼
7.  Docker forwards :8080 → container → your app
```

**Why Route 53 uses an *alias* record, not a CNAME:** ELB node IPs change as it
scales. An alias record resolves dynamically at AWS's DNS layer, works at the
zone apex (`myapp.com`, where CNAMEs are illegal), and is free to query.

**Why the EC2s have no public IP:** they don't need one. Nothing on the Internet
addresses them directly — only the ELB does, and the ELB is inside the VPC. This
is the single biggest security win of the whole layout.

---

## 4. Security Groups — the "firewall between ELB and EC2"

The intuition is right, with one correction on *where* it sits: **a Security
Group is not a box in the middle of the wire.** There's no appliance between
the ELB and the EC2. The SG is **attached to the ENI of each resource** and
evaluated **at that ENI**, on the way in and out.

```
         ┌─────────┐                          ┌─────────┐
Internet │  ELB    │                          │  EC2    │
────────►│ [SG-elb]│─────────────────────────►│[SG-app] │
   ✓     └─────────┘   evaluated HERE ────────┘   ✓
   evaluated on the                          on the EC2's own ENI
   ELB's own ENI
```

So it's not one firewall between them — it's **two checks, one at each end**.

**The chained rules:**

```
SG-elb   (on the load balancer)
  inbound   443  from  0.0.0.0/0        ← the Internet may reach the ELB
  inbound    80  from  0.0.0.0/0        ← redirect to 443
  outbound  8080 to    sg-app

SG-app   (on the EC2 instances)
  inbound   8080 from  sg-elb           ← ✅ ONLY the ELB, by SG reference
  inbound   8080 from  0.0.0.0/0        ← ❌ never do this
  outbound   all to    0.0.0.0/0        ← so it can reach NAT / RDS / ECR
```

**Referencing `sg-elb` as a source instead of an IP range** is the important
part. ELB nodes get new IPs as they scale — hardcoding CIDRs breaks. And it
expresses the intent directly: *"only whatever is the load balancer may talk to
me."*

**Stateful vs stateless** (from note 14 §8, and it matters here):

| | Security Group | NACL |
|---|---|---|
| Attached to | ENI | subnet |
| **Stateful** | ✅ reply traffic auto-allowed | ❌ must allow both directions |
| Rules | **allow only** | allow **and** deny |
| Here | does all the real work | left at default (allow all) |

Because SGs are stateful, you only write the **inbound** rule. The response
from `8080` back to the ELB's ephemeral port is allowed automatically — you
never write a rule for it. With a NACL you would have to open `1024–65535`
outbound for return traffic.

---

## 5. Why ELB → EC2 needs no special route

This is the subtle bit the diagram calls out, and it follows entirely from the
`local` route:

```
ELB node   10.10.0.42   (Public Subnet 1)
EC2        10.10.2.15   (Private Subnet 1)

Destination 10.10.2.15 matches  10.10.0.0/16 → local
```

The traffic **never leaves the VPC**, so:
- ❌ no IGW involved (that's only for `0.0.0.0/0`)
- ❌ no NAT involved (that's only for outbound-to-Internet)
- ❌ no route table entry to add — `local` is already there and can't be removed
- ✅ works **across AZs** too, over AWS's private fibre

The reachability question here is **purely a Security Group question**, not a
routing one. If the ELB's health checks fail, check `SG-app` before you touch
route tables.

---

## 6. Outbound flow — private EC2 to the Internet

Your app needs to pull the Docker image, fetch S3 configs, call a payment API,
run `apt-get update`. It has **no public IP** and lives in a private subnet.
That's what NAT is for (note 14 §7):

```
1.  EC2 10.10.2.15  →  api.stripe.com:443
        │
2.  VPC router: destination is public →  matches 0.0.0.0/0 → nat-xxxxx
        │
3.  NAT Gateway (Public Subnet 2, 10.10.1.x, holds an Elastic IP)
        │   rewrites source:  10.10.2.15:51000  →  52.66.10.5:61234
        │   records it in the translation table
        ▼
4.  VPC router (NAT's subnet uses the PUBLIC RT) → 0.0.0.0/0 → igw
        │
5.  IGW  →  Internet
        │
6.  reply comes back to 52.66.10.5:61234
        │   NAT looks up the table, rewrites dest → 10.10.2.15:51000
        ▼
7.  EC2 receives the response
```

**One-way by construction:** the table entry only exists because *the EC2*
started the conversation. An unsolicited packet arriving at `52.66.10.5` matches
nothing and is dropped. Private instances can reach out; the Internet can't
reach in.

**Note the two-hop routing:** the private subnet's route sends traffic to the
NAT, and the NAT's own subnet (public) then sends it to the IGW. **This is why
the NAT Gateway must live in a public subnet** — it needs its own
`0.0.0.0/0 → IGW` route to actually get out.

### The gap in this diagram

Only **one NAT Gateway** exists (in Public Subnet 2 / AZ-2), but **both**
private subnets route to it. That means:

- ⚠️ **AZ-2 dies → AZ-1's instances lose all outbound Internet**, even though
  AZ-1 itself is perfectly healthy. A cross-AZ single point of failure.
- 💸 Every byte from AZ-1's EC2s crosses an AZ boundary → **cross-AZ data
  transfer charges** on top of NAT processing charges.

**Fix:** one NAT Gateway per AZ, and a **separate private route table per AZ**:

```
private-rt-az1:  0.0.0.0/0 → nat-az1     (attached to Private Subnet 1)
private-rt-az2:  0.0.0.0/0 → nat-az2     (attached to Private Subnet 2)
```

**Cheaper fix for AWS-bound traffic:** NAT charges per GB processed, and pulling
Docker images from ECR is *a lot* of GB. Use **VPC Endpoints** — the traffic
stays on the AWS network and skips NAT entirely:

| Service | Endpoint type | Cost |
|---------|---------------|------|
| **S3**, DynamoDB | **Gateway** endpoint (a route table entry) | **free** |
| **ECR** (api + dkr), Secrets Manager, SSM, CloudWatch Logs | **Interface** endpoint (an ENI) | hourly + per GB, still cheaper than NAT |

For a container platform, ECR + S3 endpoints usually pay for themselves
immediately.

---

## 7. Where every piece fits

| Component | Lives in | Public IP? | Job |
|-----------|----------|-----------|-----|
| **Route 53** | global | — | name → ELB |
| **IGW** | VPC level | — | the door; 1:1 NAT for public IPs |
| **ELB** | **public** subnets, both AZs | ✅ (its nodes) | TLS termination, health checks, spread load |
| **NAT GW** | **public** subnet, per AZ | ✅ Elastic IP | outbound-only for private subnets |
| **EC2 + EBS** | **private** subnets, both AZs | ❌ | run the container |
| **VPC router** | implicit, every subnet's `.1` | — | consult route table, forward |
| **Security Group** | on each ENI | — | stateful allow-list firewall |
| **ECR** | regional service | — | Docker registry |
| **S3** | regional service | — | configs, artifacts, static files |
| **CloudFormation** | regional service | — | build/update the whole stack |
| **IAM** | global | — | who may do what |

---

## 8. Common gotchas in this exact architecture

- **One NAT Gateway shared across AZs** — the flaw called out in §6. Cheap
  until AZ-2 blinks and AZ-1 silently loses outbound.
- **NAT Gateway put in a private subnet.** It then has no path to the IGW and
  nothing works. It *must* be in a public subnet.
- **`SG-app` opened to `0.0.0.0/0`** instead of referencing `sg-elb`. The EC2 has
  no public IP so it isn't directly exposed — but anything else in the VPC can
  now reach it, and the intent is lost.
- **Health checks failing → 502 from the ELB.** Almost always `SG-app` not
  allowing the ELB, or the health check path returning non-2xx. Check the SG
  before the route tables (§5).
- **Long-lived AWS keys in GitHub secrets.** Use OIDC role assumption.
- **Tagging images `latest`.** Rollback becomes guesswork.
- **Secrets in S3 configs.** Use Secrets Manager / SSM Parameter Store.
- **Forgetting the ELB needs ≥2 subnets in ≥2 AZs.** It won't create otherwise.
- **Subnets sized too small.** ELB nodes take 2+ IPs *per AZ* and scale up under
  load; interface endpoints take one per AZ each (note 14 §5, and the ENI point
  — every IP is an ENI, not an EC2).
- **EBS is AZ-locked.** An instance in AZ-1 cannot attach a volume from AZ-2.
  Anything stateful should be in RDS/S3, not on EBS.

---

## 9. One-line summary

> **GitHub → Actions → (IAM auth) → image to ECR + configs to S3 →
> CloudFormation builds the stack.** Then: **client → Route 53 → IGW → ELB in
> the public subnets → (Security Group check) → EC2 in the private subnets** —
> that hop needs **no route**, because `local` covers the whole VPC. Going the
> other way, **EC2 → NAT Gateway in a public subnet → IGW → Internet**, one-way
> only. Public/private is decided by nothing but **which route table the subnet
> points at**.
