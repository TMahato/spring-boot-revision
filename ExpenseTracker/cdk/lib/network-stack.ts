import * as cdk from 'aws-cdk-lib';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as ecs from 'aws-cdk-lib/aws-ecs';
import * as elbv2 from 'aws-cdk-lib/aws-elasticloadbalancingv2';
import * as ssm from 'aws-cdk-lib/aws-ssm';
import { Construct } from 'constructs';

/**
 * Core network for the ExpenseTracker system.
 *
 * Replaces cloudformation-template--initial-infra.yaml. Builds the empty
 * house — nothing runs in here yet:
 *
 *   VPC 10.0.0.0/16 across 2 AZs
 *     public  /24 per AZ  -> Internet Gateway   (ALB nodes, NAT Gateways)
 *     private /24 per AZ  -> NAT Gateway in the SAME AZ  (containers)
 *   Internet-facing ALB on port 80
 *   ECS cluster
 *   Two security groups, chained: Internet -> ALB -> containers
 *
 * Everything downstream stacks need is published to SSM Parameter Store
 * under /expense-tracker/* (see the bottom of this file).
 */
export class NetworkStack extends cdk.Stack {
  public readonly vpc: ec2.Vpc;
  public readonly cluster: ecs.Cluster;
  public readonly alb: elbv2.ApplicationLoadBalancer;
  public readonly albSecurityGroup: ec2.SecurityGroup;
  public readonly containerSecurityGroup: ec2.SecurityGroup;
  public readonly httpListener: elbv2.ApplicationListener;

  constructor(scope: Construct, id: string, props?: cdk.StackProps) {
    super(scope, id, props);

    // ---------------------------------------------------------------------
    // VPC
    // ---------------------------------------------------------------------
    // One construct replaces ~25 CloudFormation resources. CDK derives the
    // Internet Gateway, the route tables, all four subnet associations and
    // the NAT Gateways from this declaration — do NOT also create them by
    // hand with CfnInternetGateway / CfnNatGateway / CfnRoute, or you end up
    // with duplicate gateways and conflicting 0.0.0.0/0 routes.
    this.vpc = new ec2.Vpc(this, 'Vpc', {
      vpcName: 'expense-tracker',
      ipAddresses: ec2.IpAddresses.cidr('10.0.0.0/16'),
      maxAzs: 2,

      // One NAT per AZ. Sharing a single NAT across AZs is a cross-AZ single
      // point of failure and doubles the data-transfer bill.
      natGateways: 2,

      subnetConfiguration: [
        {
          name: 'public',
          subnetType: ec2.SubnetType.PUBLIC,
          cidrMask: 24, // 10.0.0.0/24, 10.0.1.0/24
        },
        {
          name: 'private',
          // PRIVATE_WITH_EGRESS = no inbound from the Internet, but outbound
          // works via the NAT Gateway. This is what puts 0.0.0.0/0 -> nat-xxx
          // in each private route table.
          subnetType: ec2.SubnetType.PRIVATE_WITH_EGRESS,
          cidrMask: 24, // 10.0.2.0/24, 10.0.3.0/24
        },
      ],

      enableDnsHostnames: true,
      enableDnsSupport: true,
    });

    // Free VPC endpoint for S3. Container images layers, config downloads and
    // anything else S3-bound skip the NAT Gateway entirely, which is usually
    // the single biggest NAT data-processing saving available.
    this.vpc.addGatewayEndpoint('S3Endpoint', {
      service: ec2.GatewayVpcEndpointAwsService.S3,
    });

    // ---------------------------------------------------------------------
    // Security groups — chained, not nested
    // ---------------------------------------------------------------------
    // A security group is attached to each network interface and evaluated
    // there. It is not an appliance sitting between the ALB and the tasks.
    this.albSecurityGroup = new ec2.SecurityGroup(this, 'AlbSecurityGroup', {
      vpc: this.vpc,
      description: 'Access to the public facing load balancer',
      allowAllOutbound: true,
    });
    this.albSecurityGroup.addIngressRule(
      ec2.Peer.anyIpv4(),
      ec2.Port.tcp(80),
      'HTTP from the Internet',
    );
    this.albSecurityGroup.addIngressRule(
      ec2.Peer.anyIpv4(),
      ec2.Port.tcp(443),
      'HTTPS from the Internet',
    );

    this.containerSecurityGroup = new ec2.SecurityGroup(this, 'ContainerSecurityGroup', {
      vpc: this.vpc,
      description: 'Access to the containers',
      allowAllOutbound: true,
    });
    // Source is the ALB's security group, NOT a CIDR. ALB nodes scale in and
    // out and their private IPs change; an SG reference keeps working.
    this.containerSecurityGroup.addIngressRule(
      this.albSecurityGroup,
      ec2.Port.tcpRange(0, 65535),
      'All TCP from the public load balancer only',
    );

    // ---------------------------------------------------------------------
    // Application Load Balancer
    // ---------------------------------------------------------------------
    this.alb = new elbv2.ApplicationLoadBalancer(this, 'PublicLoadBalancer', {
      vpc: this.vpc,
      internetFacing: true,
      securityGroup: this.albSecurityGroup,
      idleTimeout: cdk.Duration.seconds(30),
      vpcSubnets: { subnetType: ec2.SubnetType.PUBLIC },
    });

    // A listener must have a default action, but no service exists yet.
    // The CloudFormation version forwarded to an empty "dummy" target group;
    // a fixed response says the same thing without the placeholder resource.
    // Later stacks attach their own target groups as listener RULES, which
    // are evaluated before this default.
    this.httpListener = this.alb.addListener('HttpListener', {
      port: 80,
      protocol: elbv2.ApplicationProtocol.HTTP,
      open: false, // ingress is managed explicitly on albSecurityGroup above
      defaultAction: elbv2.ListenerAction.fixedResponse(503, {
        contentType: 'text/plain',
        messageBody: 'No service is registered on this load balancer yet.',
      }),
    });

    // ---------------------------------------------------------------------
    // ECS cluster
    // ---------------------------------------------------------------------
    this.cluster = new ecs.Cluster(this, 'Cluster', {
      vpc: this.vpc,
      clusterName: 'expense-tracker',
      containerInsightsV2: ecs.ContainerInsights.ENABLED,
      // Lets services find each other as <name>.local instead of by IP.
      defaultCloudMapNamespace: { name: 'expense-tracker.local' },
    });

    // ---------------------------------------------------------------------
    // Publish everything downstream stacks need
    // ---------------------------------------------------------------------
    // SSM Parameter Store replaces the CloudFormation-outputs-to-S3 handshake:
    // this stack writes, later stacks read. Unlike CfnOutput exports, a value
    // can be changed while another stack is consuming it.
    const publish = (name: string, value: string) =>
      new ssm.StringParameter(this, `Param${name.replace(/[^A-Za-z0-9]/g, '')}`, {
        parameterName: `/expense-tracker/${name}`,
        stringValue: value,
      });

    publish('vpc-id', this.vpc.vpcId);
    publish('cluster-name', this.cluster.clusterName);
    publish('alb-arn', this.alb.loadBalancerArn);
    publish('alb-dns', this.alb.loadBalancerDnsName);
    publish('alb-listener-arn', this.httpListener.listenerArn);
    publish('alb-security-group-id', this.albSecurityGroup.securityGroupId);
    publish('container-security-group-id', this.containerSecurityGroup.securityGroupId);

    this.vpc.publicSubnets.forEach((subnet, i) =>
      publish(`public-subnet-${i}`, subnet.subnetId),
    );
    this.vpc.privateSubnets.forEach((subnet, i) =>
      publish(`private-subnet-${i}`, subnet.subnetId),
    );

    // Stack outputs — these land in cdk-outputs.json during CI.
    new cdk.CfnOutput(this, 'VpcId', { value: this.vpc.vpcId });
    new cdk.CfnOutput(this, 'ClusterName', { value: this.cluster.clusterName });
    new cdk.CfnOutput(this, 'ExternalUrl', {
      value: `http://${this.alb.loadBalancerDnsName}`,
      description: 'Public address of the system',
    });
    new cdk.CfnOutput(this, 'PublicSubnetIds', {
      value: this.vpc.publicSubnets.map((s) => s.subnetId).join(','),
    });
    new cdk.CfnOutput(this, 'PrivateSubnetIds', {
      value: this.vpc.privateSubnets.map((s) => s.subnetId).join(','),
    });
  }
}
