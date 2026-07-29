#!/usr/bin/env node
import 'source-map-support/register';
import * as cdk from 'aws-cdk-lib';
import { NetworkStack } from '../lib/network-stack';

const app = new cdk.App();

// CDK_DEFAULT_ACCOUNT / CDK_DEFAULT_REGION come from the ambient AWS
// credentials. In CI those are set by aws-actions/configure-aws-credentials.
const env: cdk.Environment = {
  account: process.env.CDK_DEFAULT_ACCOUNT,
  region: process.env.CDK_DEFAULT_REGION ?? 'ap-south-1',
};

new NetworkStack(app, 'ExpenseTrackerNetworkStack', {
  env,
  description: 'ExpenseTracker core network: VPC, subnets, NAT, ALB, ECS cluster',
});

// Applied to every resource in every stack — makes the bill readable and
// lets you find this project's resources in the console.
cdk.Tags.of(app).add('Project', 'ExpenseTracker');
cdk.Tags.of(app).add('ManagedBy', 'CDK');
