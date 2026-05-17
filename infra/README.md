# Infrastructure

Put infrastructure-as-code here.

Recommended structure:

```text
infra/
  terraform/
    modules/
    envs/
      dev/
      staging/
      prod/
  docker/
  k8s/
```

Production changes should require human review and environment approval.
