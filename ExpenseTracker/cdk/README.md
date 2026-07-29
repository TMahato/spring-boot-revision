# ExpenseTracker — AWS CDK infrastructure

TypeScript CDK app that builds the AWS network for the ExpenseTracker system.
Replaces the previous `cloudformation-template--initial-infra.yaml`.

See `notes/chapter-8-aws-cicd-cloudformation.md` for the concepts.

## Stacks

| Stack | Builds |
|---|---|
| `ExpenseTrackerNetworkStack` | VPC `10.0.0.0/16` over 2 AZs, public + private `/24` per AZ, IGW, one NAT Gateway per AZ, S3 gateway endpoint, internet-facing ALB, ECS cluster, chained security groups |

Nothing runs in this network yet — the ALB returns `503` until a service stack
registers targets on its listener.

## Commands

```bash
npm install          # once
npm run build        # tsc
npm run synth        # render CloudFormation into cdk.out/ (no AWS calls)
npm run diff         # what would change against the deployed stack
npm run deploy       # deploy
npm run destroy      # tear everything down
```

`npx cdk bootstrap` is required once per account/region before the first
deploy. CI does this automatically.

## Consuming the outputs

The network stack publishes to SSM Parameter Store so later stacks don't need
to import CloudFormation exports:

```
/expense-tracker/vpc-id
/expense-tracker/cluster-name
/expense-tracker/alb-arn
/expense-tracker/alb-dns
/expense-tracker/alb-listener-arn
/expense-tracker/alb-security-group-id
/expense-tracker/container-security-group-id
/expense-tracker/public-subnet-0     /expense-tracker/public-subnet-1
/expense-tracker/private-subnet-0    /expense-tracker/private-subnet-1
```

## Cost

Two NAT Gateways ≈ **$65–70/month in `ap-south-1`, idle**, plus the ALB at
~$18/month. They bill whether traffic flows or not.

```bash
npm run destroy
```

when you stop experimenting. Because CDK owns every resource, that removes all
of it — including the Elastic IPs, which otherwise keep billing on their own.
