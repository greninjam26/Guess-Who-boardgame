#!/usr/bin/env bash
#
# Checks the CloudFormation template against the decisions that keep this
# deployment free and closed.
#
# These are not style rules. Every assertion here is something that costs money
# or opens a door if it drifts, and every one of them is invisible in a diff
# once the template is a few hundred lines: an instance type, a credit mode, an
# ingress rule, a wildcard in a policy. CloudFormation will happily deploy all
# of them.
#
# It reads the template rather than calling AWS, so it runs on any machine with
# no credentials and creates nothing. `aws cloudformation validate-template`
# checks that the template is well formed; this checks that it is the template
# we agreed on.

set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
template="${1:-$here/../template.yaml}"

if [ ! -f "$template" ]; then
    echo "FAIL: no template at $template"
    exit 1
fi

# CloudFormation's short forms (!Sub, !Ref, !GetAtt) are not plain YAML, so they
# are read as unknown tags rather than resolved. Nothing asserted here depends on
# their values — only on shapes and literals — so treating them as opaque is
# enough, and it keeps this test free of an AWS SDK.
ruby -ryaml -e '
  template_path = ARGV[0]
  failures = []

  def check(failures, condition, message)
    failures << message unless condition
  end

  class CfnTag
    attr_reader :value
    def init_with(coder); @value = coder.scalar || coder.seq || coder.map; end
    def to_s; @value.to_s; end
  end

  Psych.add_domain_type("", "dummy") { |_, v| v }
  parser = Psych::ClassLoader::Restricted.new([], [])
  scanner = Psych::ScalarScanner.new(parser)
  visitor = Psych::Visitors::NoAliasRuby.new(scanner, parser)
  handler = Psych::Parser::Mark
  tree = Psych.parse_file(template_path)

  # Replace every unresolvable CloudFormation tag with its inner text so the
  # document becomes ordinary YAML.
  def strip_tags(node)
    node.tag = nil if node.respond_to?(:tag=)
    node.children.each { |c| strip_tags(c) } if node.respond_to?(:children) && node.children
    node
  end
  doc = visitor.accept(strip_tags(tree))

  resources = doc["Resources"] || {}
  outputs   = doc["Outputs"]   || {}

  def resources_of(resources, type)
    resources.select { |_, r| r["Type"] == type }
  end

  # --- the instance, and what it costs ------------------------------------
  instances = resources_of(resources, "AWS::EC2::Instance")
  check(failures, instances.size == 1, "expected exactly one EC2 instance, found #{instances.size}")
  instances.each_value do |instance|
    props = instance["Properties"] || {}
    check(failures, props["InstanceType"].to_s.include?("t3.micro"),
          "instance type is not t3.micro: #{props["InstanceType"]}")
    credits = (props["CreditSpecification"] || {})["CPUCredits"].to_s
    check(failures, credits == "standard",
          "CPUCredits must be standard or surplus credits are billable, got #{credits.inspect}")
    mappings = props["BlockDeviceMappings"] || []
    check(failures, mappings.size == 1, "expected exactly one block device")
    mappings.each do |m|
      ebs = m["Ebs"] || {}
      check(failures, ebs["VolumeType"].to_s == "gp3", "root volume is not gp3")
      check(failures, ebs["VolumeSize"].to_s == "12", "root volume is not 12 GB")
      check(failures, ebs["Encrypted"].to_s == "true", "root volume is not encrypted")
    end
  end

  # --- what the internet can reach ----------------------------------------
  groups = resources_of(resources, "AWS::EC2::SecurityGroup")
  check(failures, groups.size >= 1, "no security group")
  allowed = []
  groups.each_value do |group|
    (group["Properties"]["SecurityGroupIngress"] || []).each do |rule|
      allowed << rule["FromPort"].to_i
      check(failures, rule["IpProtocol"].to_s == "tcp",
            "non-tcp ingress rule: #{rule["IpProtocol"]}")
      check(failures, rule["FromPort"] == rule["ToPort"],
            "ingress rule spans a port range: #{rule["FromPort"]}-#{rule["ToPort"]}")
    end
  end
  check(failures, allowed.sort == [80, 443],
        "public ports must be exactly 80 and 443, found #{allowed.sort.inspect}")
  [22, 8080, 5432].each do |closed|
    check(failures, !allowed.include?(closed), "port #{closed} is exposed to the internet")
  end

  # --- the resources the design names -------------------------------------
  {
    "AWS::EC2::EIP"                => "Elastic IP",
    "AWS::S3::Bucket"              => "artifact bucket",
    "AWS::Logs::LogGroup"          => "log group",
    "AWS::IAM::Role"               => "IAM role",
    "AWS::IAM::InstanceProfile"    => "instance profile",
    "AWS::IAM::OIDCProvider"       => "GitHub OIDC provider",
    "AWS::Budgets::Budget"         => "budget",
  }.each do |type, name|
    check(failures, resources_of(resources, type).any?, "missing #{name} (#{type})")
  end

  # --- the bucket ----------------------------------------------------------
  resources_of(resources, "AWS::S3::Bucket").each_value do |bucket|
    props = bucket["Properties"] || {}
    check(failures, (props["VersioningConfiguration"] || {})["Status"].to_s == "Enabled",
          "artifact bucket is not versioned")
    blocked = props["PublicAccessBlockConfiguration"] || {}
    %w[BlockPublicAcls BlockPublicPolicy IgnorePublicAcls RestrictPublicBuckets].each do |k|
      check(failures, blocked[k].to_s == "true", "bucket does not set #{k}")
    end
    check(failures, props["BucketEncryption"], "artifact bucket is not encrypted")
  end

  # --- log retention -------------------------------------------------------
  resources_of(resources, "AWS::Logs::LogGroup").each_value do |group|
    days = (group["Properties"] || {})["RetentionInDays"].to_s
    check(failures, days == "7", "log retention must be 7 days, got #{days.inspect}")
  end

  # --- the budget must see through the credits -----------------------------
  resources_of(resources, "AWS::Budgets::Budget").each_value do |budget|
    data = ((budget["Properties"] || {})["Budget"] || {})
    types = data["CostTypes"] || {}
    check(failures, types["IncludeCredit"].to_s == "false",
          "budget includes credits, so it stays silent until they run out")
    check(failures, types["IncludeRefund"].to_s == "false", "budget includes refunds")
  end

  # --- no policy may be broader than it needs ------------------------------
  parameter_statements = 0
  resources_of(resources, "AWS::IAM::Role").each_value do |role|
    (role["Properties"]["Policies"] || []).each do |policy|
      (policy["PolicyDocument"]["Statement"] || []).each do |statement|
        actions = Array(statement["Action"]).map(&:to_s)
        resource_arns = Array(statement["Resource"]).map(&:to_s)

        check(failures, !actions.include?("ssm:*"), "a policy grants ssm:*")
        check(failures, !actions.include?("*"), "a policy grants every action")

        if actions.any? { |a| a.start_with?("ssm:") && a.include?("Parameter") }
          parameter_statements += 1
          resource_arns.each do |arn|
            check(failures, arn.include?("/guesswho/demo/db-password"),
                  "parameter policy is not scoped to the one parameter: #{arn}")
            check(failures, !arn.end_with?("*"),
                  "parameter policy ends in a wildcard: #{arn}")
          end
        end
      end
    end
  end
  check(failures, parameter_statements >= 1, "nothing grants access to the database password")

  # --- tags ----------------------------------------------------------------
  taggable = resources.select { |_, r| (r["Properties"] || {}).key?("Tags") }
  check(failures, taggable.any?, "no resource carries tags")
  taggable.each do |name, resource|
    tags = resource["Properties"]["Tags"].map { |t| [t["Key"], t["Value"].to_s] }.to_h
    check(failures, tags["Project"] == "guess-who", "#{name} is missing Project=guess-who")
    check(failures, tags["Environment"] == "demo", "#{name} is missing Environment=demo")
  end

  # --- outputs the runbook needs ------------------------------------------
  %w[InstanceId ElasticIp ArtifactBucketName GitHubDeployRoleArn].each do |name|
    check(failures, outputs.key?(name), "missing output #{name}")
  end

  if failures.empty?
    puts "template contract: #{resources.size} resources checked, all constraints hold"
  else
    failures.each { |f| puts "FAIL: #{f}" }
    exit 1
  end
' "$template"
