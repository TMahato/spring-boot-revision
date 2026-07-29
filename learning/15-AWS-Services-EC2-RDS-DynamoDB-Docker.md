# AWS Services — EC2, RDS, DynamoDB, and running your code in Docker

This note follows on from [14 — Region / VPC / AZ / Subnets](./14-AWS-Region-VPC-AvailabilityZone-Subnets.md).
That one covered *where* things live (the network). This one covers *what
actually runs* in there — the compute box, the database, and how a client on
the Internet reaches your code.

## 0. The mental model

```
Internet / client
      │  http://<public-ip>:8080
      ▼
┌─────────────────────────────────────────┐
│ EC2 instance  (a virtual machine)       │   ← public subnet
│   └── Docker container                  │
│         └── your Spring Boot app :8080  │
└─────────────────┬───────────────────────┘
                  │  jdbc:mysql://mydb.xxxx.rds.amazonaws.com:3306
                  ▼
┌─────────────────────────────────────────┐
│ RDS MySQL  (AWS-managed EC2 you can't   │   ← private subnet
│             log into)                   │
└─────────────────────────────────────────┘
```

Everything in AWS compute is, underneath, **a machine in a datacenter**. The
services differ only in **how much of that machine AWS manages for you**.

---

## 1. EC2 — the virtual machine

**EC2 (Elastic Compute Cloud)** is a **virtual machine** running on AWS's
physical servers. It is the rawest form of compute: you get an OS, an IP, and
full root access. Nothing is installed for you.

- A physical server in an AZ is sliced by a **hypervisor** (AWS's is called
  **Nitro**) into many isolated VMs. Your instance is one slice.
- You choose an **instance type** — this is the "size" of the machine:

  | Type | Family meaning | Example spec |
  |------|----------------|--------------|
  | `t3.micro` | **T** = burstable, cheap | 2 vCPU / 1 GB |
  | `m5.large` | **M** = general purpose | 2 vCPU / 8 GB |
  | `c5.xlarge` | **C** = compute optimised | 4 vCPU / 8 GB |
  | `r5.large` | **R** = RAM optimised | 2 vCPU / 16 GB |

- You choose an **AMI** (Amazon Machine Image) — the disk image it boots from:
  Amazon Linux, Ubuntu, Windows, or a custom image with your software baked in.
- Storage is **EBS** — a network-attached virtual disk. It survives stop/start,
  but is **wiped on terminate** unless you say otherwise.
- It lives in **one subnet, in one AZ** (see note 14). It gets a **private IP**
  always, and a **public IP** only if the subnet is public and you asked for one.

**You are responsible for**: OS patching, installing Java/Docker, restarting
after a crash, backups, scaling, security hardening. That is the trade for the
control.

---

## 2. RDS — managed MySQL

**RDS (Relational Database Service)** is AWS running **MySQL / PostgreSQL /
MariaDB / Oracle / SQL Server** *for* you.

Your intuition is right: **RDS is an EC2 instance under the hood** — AWS
provisions a VM, installs and configures the database engine on it, and hands
you only a **connection endpoint**:

```
mydb.c9xk2plq8abc.ap-south-1.rds.amazonaws.com : 3306
```

The difference is **you cannot SSH into it**. There is no OS access, no
`/etc/my.cnf` to edit — you configure it through **parameter groups** instead.
It runs in an AWS-owned account, with an ENI projected into *your* subnet.

**What AWS does for you**

| Job | Self-managed MySQL on EC2 | RDS |
|-----|---------------------------|-----|
| Install / configure engine | you | **AWS** |
| OS + DB patching | you | **AWS** (in a maintenance window) |
| Automated backups | you write cron + S3 | **AWS**, point-in-time restore up to 35 days |
| Failover on AZ loss | you build it | **Multi-AZ**: automatic, ~60s |
| Read scaling | you set up replication | **Read replicas**, one click |
| Monitoring | you install it | CloudWatch + Performance Insights |
| Root/OS access | ✅ yes | ❌ **no** |
| Cost | cheaper per hour | ~2× the raw EC2 price |

**Multi-AZ** is the headline feature: RDS keeps a **synchronous standby in a
second AZ**. If the primary's AZ dies, AWS flips the DNS endpoint to the
standby automatically. Your app keeps using the same hostname — it just needs
to reconnect. (The standby is **not** readable; it is purely for failover.
Read scaling is a separate thing — **read replicas**, which are asynchronous.)

**Where to put it**: RDS belongs in a **private subnet**, with a Security Group
that allows `3306` **only from the app's Security Group** — never from
`0.0.0.0/0`.

```
DB Security Group:
  inbound  3306  source = sg-app-servers      ✅
  inbound  3306  source = 0.0.0.0/0           ❌ never
```

---

## 3. Scaling — vertical vs horizontal

| | **Vertical (scale up)** | **Horizontal (scale out)** |
|--|------------------------|----------------------------|
| Means | make the machine **bigger** | add **more machines** |
| Example | `t3.micro` → `m5.4xlarge` | 1 EC2 → 10 EC2 behind a load balancer |
| Limit | the biggest instance type that exists | effectively unlimited |
| Downtime | usually **yes** (reboot) | no |
| Works for | databases (hard to shard) | stateless app servers |

**How each AWS service scales:**

| Service | Vertical | Horizontal |
|---------|----------|------------|
| **EC2** | change instance type — **stop, change, start** (manual, downtime) | **Auto Scaling Group** adds/removes instances automatically |
| **RDS** | change instance class — manual, brief downtime (Multi-AZ reduces it to a failover) | **read replicas** only; writes stay on one node |
| **Aurora Serverless v2** | **automatic vertical** — capacity scales in seconds with load | up to 15 read replicas |
| **DynamoDB** | ❌ not a thing — there is no "instance size" | **automatic horizontal** — AWS partitions data across nodes |

> ⚠️ Worth correcting a common assumption: **DynamoDB does not do vertical
> scaling.** It's serverless — there is no machine to make bigger. It scales
> **horizontally** by splitting your table across more partitions, invisibly.
> The AWS service that does *automatic vertical* scaling is **Aurora
> Serverless v2**. For plain **RDS**, vertical scaling exists but **you**
> trigger it (change the instance class); AWS only performs the mechanics.

---

## 4. DynamoDB — the NoSQL alternative

**DynamoDB** is AWS's managed **key-value / document** database. There is no
server, no instance type, no version to patch — you create a **table** and use it.

- Data is spread across **partitions** by the **partition key**'s hash. Adding
  data adds partitions; AWS handles it.
- Two capacity modes:
  - **On-demand** — pay per request, scales instantly, zero planning.
  - **Provisioned** — you set RCU/WCU (read/write capacity units), optionally
    with auto-scaling. Cheaper for steady, predictable load.
- Single-digit-millisecond latency at essentially any scale.
- The trade: **no joins, no complex queries, no ad-hoc `WHERE`**. You must
  design the table around your **access patterns** up front. (See note 12 for
  SQL vs NoSQL and CAP.)

**Rule of thumb:** relational data with joins and transactions → **RDS**.
Massive scale, simple key lookups, unpredictable traffic → **DynamoDB**.

---

## 5. Your code — Docker on EC2

The flow you described:

```
[your laptop]  docker build -t myapp:1.0 .
        │
        ▼
[ECR]  docker push  →  123456789.dkr.ecr.ap-south-1.amazonaws.com/myapp:1.0
        │
        ▼
[EC2]  docker pull … && docker run -d -p 8080:8080 myapp:1.0
```

- **ECR (Elastic Container Registry)** is AWS's private Docker registry — the
  AWS equivalent of Docker Hub. The EC2 instance pulls from it using an **IAM
  role**, not a password.
- `-p 8080:8080` maps the **container port** to the **host (EC2) port**. This
  matters: the outside world connects to the **EC2's** port, and Docker
  forwards it into the container. If you forget `-p`, the app runs but is
  unreachable.
- **Never bake credentials into the image.** Attach an **IAM role** to the EC2
  instance instead — the SDK picks it up automatically. Database passwords go
  in **Secrets Manager** or **SSM Parameter Store**.

**Where the container runs — the options ladder:**

| Option | You manage | Good for |
|--------|-----------|----------|
| **Docker on plain EC2** | OS, Docker, restarts, scaling | learning, full control, cheap single-box setups |
| **ECS on EC2** | the EC2 fleet | container orchestration, you still own the hosts |
| **ECS on Fargate** | nothing — no servers at all | most microservices; you just give it a task definition |
| **EKS** | Kubernetes config | teams already invested in k8s |
| **Lambda** | nothing; no long-running process | event-driven, spiky, short tasks |

Running `docker run` by hand on an EC2 is the right way to *understand* it.
For anything real, move to **ECS/Fargate** — you stop caring about the machine
entirely.

---

## 6. How the client actually reaches you

Your description — *"the client calls the IP address and port of my EC2"* — is
exactly what happens in the simplest setup:

```
client → http://13.234.56.78:8080 → EC2 (public subnet) → Docker → app
```

For this to work, **all four** must be true (same list as note 14 §6):

1. The EC2 is in a **public subnet** (route table has `0.0.0.0/0 → IGW`)
2. It has a **public IP or Elastic IP**
3. Its **Security Group** allows inbound `8080` (or `80`/`443`)
4. The **NACL** allows the traffic both ways (default NACL already does)

### Why you don't ship it that way

| Problem with raw `IP:port` | Fix |
|----------------------------|-----|
| Public IP **changes on stop/start** | **Elastic IP** (static), or better — a load balancer |
| Users won't type `:8080` | **ALB** listens on 80/443 and forwards to 8080 |
| No HTTPS | **ACM certificate** on the ALB — free, auto-renewing |
| Ugly IP, not a name | **Route 53** → `api.myapp.com` |
| One EC2 = single point of failure | **ALB + Auto Scaling Group across 2 AZs** |

The production shape:

```
client
  │ https://api.myapp.com
  ▼
Route 53  (DNS)
  │
  ▼
ALB  (public subnets, AZ-a + AZ-b, TLS terminates here)
  │  forwards :443 → :8080
  ├──────────────┬──────────────
  ▼              ▼
EC2/Fargate    EC2/Fargate      (private subnets)
  └──────┬───────┘
         ▼
     RDS MySQL   (private subnet, Multi-AZ)
```

Note the shift: once an ALB is in front, **the app instances move to private
subnets** and lose their public IPs entirely. Only the ALB is exposed. Outbound
traffic (pulling images, calling APIs) then goes through the **NAT Gateway**.

---

## 7. The managed-service spectrum

Every AWS compute/database choice is the same trade — **control vs. operational
burden**:

```
more control                                          less burden
◄──────────────────────────────────────────────────────────────►
EC2          ECS/EC2      ECS/Fargate      Lambda
MySQL-on-EC2      RDS         Aurora      Aurora Serverless      DynamoDB
```

You pay a premium (roughly 1.5–2×) for managed services, and you get back the
time you'd otherwise spend patching, backing up, and building failover. For a
small team that is almost always the right trade.

---

## 8. Common gotchas

- **Putting RDS in a public subnet** so you can connect from your laptop. Use a
  **bastion host** or **SSM Session Manager** port-forward instead.
- **Security Group allowing `3306` from `0.0.0.0/0`** — the single most common
  way databases get compromised.
- **Forgetting `-p` on `docker run`** — the app starts, logs look fine, and
  nothing can reach it.
- **Assuming the public IP is stable.** It changes every stop/start. Use an
  **Elastic IP** or an ALB.
- **Storing DB passwords in the Docker image or env vars in a script.** Use
  **Secrets Manager**.
- **Data on the instance store / a terminated EBS volume** — gone permanently.
  Anything stateful belongs in RDS, DynamoDB, S3 or EFS, never on the EC2 disk.
- **Single AZ everything** — one EC2, one RDS with Multi-AZ off. Cheap until
  the AZ blinks.
- **Leaving a `t3.large` + RDS + NAT Gateway running in a dev account.** The
  bill arrives regardless of whether you used them.
- **Assuming Multi-AZ gives you read scaling.** It does not — the standby is
  invisible. That's what **read replicas** are for.

---

## 9. One-line summary

> **EC2** = *a virtual machine you fully control* → **Docker on it** = *how
> your code is packaged and run* → **RDS** = *an EC2 running MySQL that AWS
> manages and you can't log into* → **DynamoDB** = *serverless NoSQL that
> scales horizontally on its own* → and in production, *clients hit an **ALB**
> on a **Route 53** name, not an EC2's IP and port*.
