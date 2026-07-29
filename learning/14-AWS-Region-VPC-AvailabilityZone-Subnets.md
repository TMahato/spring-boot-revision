# AWS Networking Basics — Region → VPC → Availability Zone → Subnets

The core mental model of AWS networking is a set of **nested boxes**. Each layer
sits inside the one before it:

```
AWS (global)
 └── Region  (us-east-1, ap-south-1, …)          — a geographic location
      └── VPC  (10.0.0.0/16)                     — your private network in that Region
           ├── Availability Zone 1 (ap-south-1a) — one (or more) physical datacenters
           │    ├── Subnet S1  (10.0.1.0/24)     — public
           │    └── Subnet S2  (10.0.2.0/24)     — private
           └── Availability Zone 2 (ap-south-1b)
                ├── Subnet S3  (10.0.3.0/24)     — public
                └── Subnet S4  (10.0.4.0/24)     — private
```

The picture from the whiteboard: one **Region** contains one **VPC**; the VPC
spans **AZ1** and **AZ2**; each AZ holds **two subnets** (S1/S2 and S3/S4); an
**Internet Gateway (IGW)** hangs off the VPC and is the only door to the
**Internet**.

---

## 1. Region

A **Region** is a **separate geographic area** where AWS runs infrastructure —
e.g. `us-east-1` (N. Virginia), `eu-west-1` (Ireland), `ap-south-1` (Mumbai).

- Each Region is **fully independent**: its own power, network backbone and
  isolated failure domain. A Region going down does **not** take others with it.
- Most AWS resources are **Region-scoped**. An EC2 instance, VPC or RDS database
  created in `ap-south-1` simply **does not exist** in `us-east-1`.
- A few services are **global**: IAM, Route 53, CloudFront, S3 bucket namespace.
- Data does **not** move between Regions automatically — you must replicate it
  explicitly (S3 cross-region replication, RDS read replicas, etc.).

**How to choose a Region**

| Factor | Why it matters |
|--------|----------------|
| **Latency** | Pick the Region closest to your users (Indian users → `ap-south-1`) |
| **Compliance / data residency** | GDPR, RBI, HIPAA may force data to stay in-country |
| **Price** | The same instance costs different amounts per Region (`us-east-1` is usually cheapest) |
| **Service availability** | New services launch in big Regions first; not every service exists everywhere |

---

## 2. VPC (Virtual Private Cloud)

A **VPC** is **your own private, logically isolated network inside a Region** —
think "your personal datacenter network in AWS". Nothing outside it can reach
in unless you explicitly open a path.

- A VPC **lives in exactly one Region**, but **spans all AZs** of that Region.
  (In the drawing, the VPC box wraps both AZ1 and AZ2.)
- You define its private IP range with a **CIDR block**, e.g. `10.0.0.0/16` →
  65,536 addresses (`10.0.0.0` – `10.0.255.255`).
- Every account gets a **default VPC** per Region with public subnets already
  wired up — convenient for demos, but **create your own VPC** for real work.
- VPCs are isolated from each other by default. To connect them you need
  **VPC Peering**, **Transit Gateway**, or **PrivateLink**.

**CIDR quick reference**

| CIDR | Usable-ish size | Typical use |
|------|-----------------|-------------|
| `/16` | ~65,536 IPs | whole VPC |
| `/20` | ~4,096 IPs | large subnet |
| `/24` | 256 IPs (**251 usable**) | normal subnet |
| `/28` | 16 IPs (11 usable) | tiny subnet |

> AWS **reserves 5 IPs in every subnet**: network address, VPC router, DNS,
> future use, and broadcast. So a `/24` gives you **251**, not 256.

Pick a range that does **not** overlap with your on-prem network or other VPCs,
otherwise peering/VPN later becomes painful. Use the RFC 1918 private ranges:
`10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`.

---

## 3. IPv4 and IPv6

Everything above assumed IPv4 (`10.0.0.0/16`). AWS VPCs can also run **IPv6**,
and the two behave quite differently — worth understanding before designing a
network.

### What they are

| | **IPv4** | **IPv6** |
|--|----------|----------|
| Address size | **32 bits** | **128 bits** |
| Written as | 4 decimal octets — `10.0.1.25` | 8 hex groups — `2406:da1a:aef:1f00::25` |
| Total space | ~4.3 billion | ~340 undecillion (3.4 × 10³⁸) |
| Private ranges | RFC 1918 (`10/8`, `172.16/12`, `192.168/16`) | no NAT needed — every address is globally routable |
| Why it exists | the original Internet | IPv4 ran out of addresses |

An IPv6 address can be shortened: leading zeros in a group are dropped and one
run of all-zero groups collapses to `::`.

```
2406:da1a:0aef:1f00:0000:0000:0000:0025
2406:da1a:aef:1f00::25            ← same address, compressed
```

### Classful addressing — the history behind CIDR (IPv4)

Before 1993, an IPv4 address was split into **network part** and **host part**
at a **fixed boundary** decided by the **first few bits** of the address. You
did not write a `/24` — the class *told* you where the split was.

| Class | Leading bits | First octet | Network / host split | Networks | Hosts each | Modern equivalent |
|-------|--------------|-------------|----------------------|----------|-----------|-------------------|
| **A** | `0` | 1–126 | `N.H.H.H` | 128 | ~16.7 M | `/8` |
| **B** | `10` | 128–191 | `N.N.H.H` | 16,384 | ~65,534 | `/16` |
| **C** | `110` | 192–223 | `N.N.N.H` | ~2 M | 254 | `/24` |
| **D** | `1110` | 224–239 | multicast | — | — | — |
| **E** | `1111` | 240–255 | reserved / experimental | — | — | — |

`127.x.x.x` is carved out of Class A for **loopback** (`127.0.0.1`).

So `10.1.2.3` was automatically "network `10`, host `1.2.3`" — a Class A. No
mask needed; everyone just knew.

**Why it failed — the wasteful middle**

The only sizes on offer were **16.7 M**, **65 K** and **254**.

- A company needing **500 hosts** was too big for a Class C (254) → it was
  handed a **Class B (65,534)** and wasted ~65,000 addresses.
- A university given a Class A burned **16.7 million** addresses to run a few
  thousand machines.

This "IPv4 exhaustion by rounding up" is what drove **CIDR** (Classless
Inter-Domain Routing, RFC 1519) in 1993.

**What CIDR changed**

CIDR **threw away the classes** and made the split **explicit and arbitrary**
via the prefix length:

```
Classful :  10.0.0.0            → split fixed at /8   (because it starts with 0)
CIDR     :  10.0.0.0/16         → split wherever you say
            10.0.0.0/22         → 1,022 hosts — the "500 hosts" case, no waste
```

Two consequences that matter daily in AWS:

- **VLSM** (Variable Length Subnet Masking) — you can slice one block into
  differently-sized subnets (`10.0.0.0/16` → a `/24` here, a `/20` there). This
  is exactly what you do carving subnets out of a VPC.
- **Route summarization** — many adjacent networks collapse into one route
  entry, keeping Internet routing tables from exploding.

**Where the classes still show up**

The RFC 1918 private ranges are class-shaped leftovers, which is why they look
the way they do:

| Range | Old class | CIDR |
|-------|-----------|------|
| `10.0.0.0 – 10.255.255.255` | one Class A | `10.0.0.0/8` |
| `172.16.0.0 – 172.31.255.255` | 16 Class Bs | `172.16.0.0/12` |
| `192.168.0.0 – 192.168.255.255` | 256 Class Cs | `192.168.0.0/16` |

> **AWS is entirely classless.** Nothing in a VPC cares about classes — you
> always give an explicit prefix (`10.0.0.0/16`). The terms survive only in
> conversation ("a class C sized network" meaning ~254 hosts) and in the shape
> of the RFC 1918 ranges above. IPv6 never had classes at all.

### How AWS uses them

- A VPC **always** has an IPv4 CIDR — it is mandatory. IPv6 is **optional and
  additive**: you enable **dual-stack**, you don't replace IPv4.
- IPv6 CIDRs are **assigned by AWS**, not chosen by you: you get a fixed
  **`/56` for the VPC**, and you carve **`/64` per subnet** (a `/64` is the
  standard subnet size in IPv6 — 18 quintillion addresses, so subnet sizing
  stops being a problem).
- You can also bring your own range (BYOIP), but the default is AWS-allocated.
- **All IPv6 addresses in AWS are public/globally routable.** There is no
  "private IPv6" equivalent of `10.0.0.0/8`.

```
VPC   IPv4: 10.0.0.0/16          IPv6: 2406:da1a:aef:1f00::/56
S1    IPv4: 10.0.1.0/24          IPv6: 2406:da1a:aef:1f00::/64
S2    IPv4: 10.0.2.0/24          IPv6: 2406:da1a:aef:1f01::/64
```

### Internet access differs

Because IPv6 addresses are already public, **there is no NAT for IPv6**.
Reachability is controlled purely by routing:

| Goal | IPv4 | IPv6 |
|------|------|------|
| Public subnet, inbound + outbound | `0.0.0.0/0 → IGW` + public/Elastic IP | `::/0 → IGW` |
| Private subnet, **outbound only** | `0.0.0.0/0 → NAT Gateway` | `::/0` → **Egress-Only Internet Gateway** |
| Fully private | no default route | no default route |

The **Egress-Only Internet Gateway (EIGW)** is the IPv6 counterpart of a NAT
Gateway: it allows outbound connections and blocks inbound ones — but it does
**no address translation**, and unlike NAT Gateway it is **free** and
VPC-level (not per-AZ).

> Danger: attaching an IPv6 CIDR and pointing `::/0` at the plain **IGW** makes
> every instance in that subnet **directly reachable from the Internet**, even
> if it sits in what you call a "private" subnet. Use an **EIGW** for private
> subnets, and remember Security Groups/NACLs need **explicit IPv6 rules** —
> a rule for `0.0.0.0/0` does **not** cover `::/0`.

### Practical notes

- Security Group and NACL rules are per-family. Every rule you care about needs
  both a `0.0.0.0/0`-style and a `::/0`-style entry.
- A subnet's **auto-assign IPv6** setting must be on for instances to actually
  get an IPv6 address; instance types must be **Nitro-based** for IPv6-only
  subnets.
- **IPv6-only subnets** exist (no IPv4 at all) — they solve IPv4 exhaustion in
  big EKS clusters, but many services still need IPv4, so **dual-stack** is the
  common production choice.
- Loopback: `127.0.0.1` (IPv4) ≡ `::1` (IPv6). "All addresses":
  `0.0.0.0/0` ≡ `::/0`.
- IPv4 public addresses are now **billed hourly** in AWS — an extra reason
  dual-stack/IPv6 is getting popular.

---

## 4. Availability Zone (AZ)

An **AZ** is **one or more physically separate datacenters** within a Region,
with independent power, cooling and networking.

- Named as Region + letter: `ap-south-1a`, `ap-south-1b`, `ap-south-1c`.
- Most Regions have **3+ AZs**; they are kilometres apart (so a fire/flood hits
  only one) but linked by **low-latency, high-bandwidth private fibre**
  (single-digit-millisecond, effectively free traffic between them).
- **AZs are the unit of fault tolerance.** If AZ1 dies, workloads in AZ2 keep
  running — *only if you actually deployed into both*.
- The AZ letter is **randomized per AWS account** — your `ap-south-1a` may be a
  different physical building than someone else's `ap-south-1a`. (The stable
  identifier is the **AZ ID**, e.g. `aps1-az1`.)

**Rule of thumb:** always spread across **at least 2 AZs**. That is what makes
Multi-AZ RDS, an Auto Scaling Group, or an Application Load Balancer actually
highly available. Many AWS services *require* subnets in ≥2 AZs.

---

## 5. Subnets

A **subnet** is a **slice of the VPC's IP range that lives inside exactly one
AZ**. This is where your actual resources (EC2, RDS, Lambda ENIs, containers)
get their IP addresses.

Key rules:

- A subnet **cannot span AZs**. One subnet = one AZ. This is why the diagram has
  4 subnets: 2 per AZ.
- A subnet's CIDR must be **inside** the VPC CIDR and must **not overlap** other
  subnets.
- What makes a subnet "public" or "private" is **not a setting** — it is the
  **route table** attached to it.

### Public vs Private subnet

| | **Public subnet** | **Private subnet** |
|--|-------------------|--------------------|
| Route table | has a route `0.0.0.0/0 → IGW` | **no** IGW route |
| Reachable from Internet | **Yes** (with a public/Elastic IP) | **No** |
| Outbound to Internet | direct via IGW | via **NAT Gateway** placed in a public subnet |
| Typical contents | Load balancers, bastion/jump hosts, NAT Gateway | App servers, databases, caches, internal services |

Classic layout (matches the drawing, per AZ):

```
AZ1: S1 = public  (ALB / NAT)     S2 = private (app + DB)
AZ2: S3 = public  (ALB / NAT)     S4 = private (app + DB)
```

Traffic flow for a typical web app:

```
Internet → IGW → ALB (public subnet) → App server (private subnet) → RDS (private subnet)
```

and for outbound patching from a private instance:

```
App server (private) → NAT Gateway (public subnet) → IGW → Internet
```

> NAT is **one-way**: private instances can reach out, but the Internet cannot
> initiate a connection back in. NAT Gateway is per-AZ and **costs money**
> (hourly + per-GB) — a common surprise on the bill.

---

## 6. Internet Gateway (IGW)

The **IGW** is the component drawn hanging off the left of the VPC, connecting
it to the **Internet cloud**.

- **One IGW per VPC**, attached at the **VPC level** (not to an AZ or subnet).
- It is **horizontally scaled, redundant and highly available** by design — not
  a bottleneck, no single point of failure, nothing to size.
- It does two jobs:
  1. Provides the **target for `0.0.0.0/0`** routes → Internet access.
  2. Performs **1:1 NAT** between an instance's private IP and its public /
     Elastic IP.
- An instance is only reachable from the Internet if **all** of these are true:
  1. Its subnet's route table points `0.0.0.0/0` at the IGW,
  2. It has a **public IP or Elastic IP**,
  3. Its **Security Group** allows the inbound port,
  4. The subnet's **NACL** allows the traffic both ways.

---

## 7. NAT (Network Address Translation)

**NAT** is the trick that lets a machine with a **private IP** talk to the
Internet using a **shared public IP**, while staying unreachable from outside.

### Why it exists

IPv4 has only ~4.3 billion addresses — nowhere near enough. So most machines
get a **private IP** (`10.x`, `172.16–31.x`, `192.168.x`) that is **not routable
on the Internet**. A packet with source `10.0.2.15` sent to Google would be
dropped, and even if it arrived, Google would have no idea where to reply.

NAT solves this by **rewriting the packet's source address** on the way out and
**rewriting it back** on the way in.

### How it actually works

The device doing NAT keeps a **translation table** mapping
`(private IP : port)` ⇄ `(public IP : port)`.

```
App server 10.0.2.15  →  wants  142.250.183.14:443 (google.com)

  outbound, before NAT   src 10.0.2.15:51000    dst 142.250.183.14:443
  outbound, after  NAT   src 52.66.10.5:61234   dst 142.250.183.14:443
                              ↑ NAT Gateway's public IP, new port

  NAT table:  10.0.2.15:51000  ⇄  52.66.10.5:61234

  inbound reply, arrives  src 142.250.183.14:443  dst 52.66.10.5:61234
  inbound reply, after    src 142.250.183.14:443  dst 10.0.2.15:51000
```

Because many private hosts are multiplexed onto one public IP by **varying the
port**, this is properly called **PAT / NAT overload** — and it's what AWS NAT
Gateway does.

### The key property: one-way

The table entry is **created by outbound traffic only**. An unsolicited packet
from the Internet hitting `52.66.10.5` matches nothing in the table, so the NAT
device has no idea which private host to send it to — it **drops it**.

> That's the whole security value: private instances can *reach out* (OS
> patches, `pip install`, calling an external API), but nothing on the Internet
> can *initiate* a connection *in*.

### The two kinds of NAT in AWS

Both appear in this note; they are different things:

| | **1:1 NAT (IGW)** | **Many:1 NAT (NAT Gateway)** |
|--|-------------------|------------------------------|
| Who does it | **Internet Gateway** | **NAT Gateway / NAT instance** |
| Mapping | one private IP ⇄ one **public/Elastic IP** | many private IPs ⇄ **one** Elastic IP |
| Lives in | VPC level | a **public subnet**, one per AZ |
| Instance needs | its own public IP | **no** public IP |
| Direction | **both** ways | **outbound only** |
| Used by | public subnets (ALB, bastion) | private subnets (app, DB) |

So: an EC2 box in a public subnet with an Elastic IP is *also* using NAT —
just the 1:1 kind, done invisibly by the IGW. The instance's OS only ever sees
`10.0.1.20`; the public IP is never configured on the NIC.

### NAT Gateway in practice

```
Route table "private-a":
  10.0.0.0/16 → local
  0.0.0.0/0   → nat-gw-a          ← the NAT Gateway lives in public subnet S1

App server (private S2) → NAT-GW-a (public S1) → IGW → Internet
```

- The NAT Gateway **must sit in a public subnet** (it needs its own
  `0.0.0.0/0 → IGW` route to actually get out) and holds an **Elastic IP**.
- **One per AZ.** If AZ-a's NAT dies, AZ-b's instances must not depend on it —
  otherwise you've created a cross-AZ single point of failure *and* pay
  cross-AZ data transfer.
- Fully managed: scales to 100 Gbps, no patching, no HA to build.
- **Costs money**: ~hourly charge **plus per-GB processed**, 24×7 whether idle
  or not. This is one of the most common surprise line items on an AWS bill.
- A **NAT instance** (an EC2 running NAT yourself) is the cheap legacy
  alternative — you own the HA, scaling and patching. Rarely worth it now.

### NAT and IPv6

**There is no NAT for IPv6** — every IPv6 address is already globally unique
and routable, so translation is pointless. The one-way behaviour is instead
provided by routing, via the **Egress-Only Internet Gateway** (see §3):

| | IPv4 | IPv6 |
|--|------|------|
| Outbound-only from private subnet | **NAT Gateway** (translates, costs $) | **EIGW** (no translation, free) |

### Cost-saving note

Traffic to **S3, DynamoDB, ECR, Secrets Manager…** does *not* need to leave via
NAT. Use a **VPC Endpoint** (Gateway endpoint for S3/DynamoDB — free;
Interface/PrivateLink endpoint for the rest) and the traffic stays on the AWS
network, bypassing NAT Gateway data charges entirely.

---

## 8. The glue: route tables, security groups, NACLs

| Component | Scope | Stateful? | Rules | Purpose |
|-----------|-------|-----------|-------|---------|
| **Route Table** | subnet | — | destination → target | decides *where* traffic goes (IGW, NAT, peering, local) |
| **Security Group** | ENI / instance | **Stateful** (reply auto-allowed) | **allow only** | instance-level firewall — the one you use daily |
| **NACL** | subnet | **Stateless** (must allow both directions) | allow **and** deny, numbered | coarse subnet-level firewall, default allows everything |

- `local` route (`10.0.0.0/16 → local`) is added automatically and **cannot be
  removed** — it's why every subnet in the VPC can talk to every other subnet,
  across AZs, by default.
- Security Groups can reference **other Security Groups** as the source — e.g.
  "DB SG allows 5432 from App SG". Cleaner and safer than hardcoding IPs.

---

## 9. Putting it together — a reference build

```
Region: ap-south-1
VPC:    10.0.0.0/16
IGW:    attached to VPC

AZ ap-south-1a                         AZ ap-south-1b
├── S1 public   10.0.1.0/24            ├── S3 public   10.0.3.0/24
│     ALB node, NAT-GW-a               │     ALB node, NAT-GW-b
└── S2 private  10.0.2.0/24            └── S4 private  10.0.4.0/24
      EC2/ECS app, RDS primary               EC2/ECS app, RDS standby

Route table "public"   : 10.0.0.0/16 → local, 0.0.0.0/0 → IGW      (S1, S3)
Route table "private-a": 10.0.0.0/16 → local, 0.0.0.0/0 → NAT-GW-a (S2)
Route table "private-b": 10.0.0.0/16 → local, 0.0.0.0/0 → NAT-GW-b (S4)
```

Why this shape:
- **2 AZs** → survives a datacenter failure (ALB and ASG spread automatically).
- **Public/private split** → databases and app servers are never directly
  exposed; only the load balancer is.
- **NAT per AZ** → an AZ failure doesn't kill the other AZ's outbound traffic
  (a single shared NAT would be a cross-AZ single point of failure, plus
  cross-AZ data charges).

---

## 10. Common gotchas

- Choosing a **VPC CIDR that overlaps** your on-prem/other VPC → peering and VPN
  become impossible without re-building. Plan IP space upfront.
- Making subnets **too small** (`/28`) → run out of IPs when Auto Scaling or EKS
  pods grow. VPC CIDR can be extended, but a subnet CIDR **cannot be resized**.
- Forgetting the **5 reserved IPs** per subnet.
- Putting a database in a **public subnet** "just to test" — then leaving it.
- Assuming a subnet is public because it *has* a public IP — without the **IGW
  route** it still can't reach the Internet.
- Deploying to a **single AZ** and calling it highly available.
- Leaving a **NAT Gateway** running in a dev account — it bills 24×7 whether
  used or not.
- Mixing up **SG (stateful, allow-only, instance)** with **NACL (stateless,
  allow+deny, subnet)** — with a NACL you must open the **ephemeral port range**
  (1024–65535) for return traffic.

---

## 11. One-line summary

> **Region** = *where in the world* → **VPC** = *your private network there* →
> **Availability Zone** = *which datacenter, for fault tolerance* →
> **Subnet** = *an IP slice in one AZ, made public or private by its route
> table* → **IGW** = *the single door to the Internet*.
