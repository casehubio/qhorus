---
id: PP-20260618-d9aeef
title: "Consumers adding an optional qhorus module with JPA entities must register its package in quarkus.hibernate-orm.qhorus.packages"
type: rule
scope: repo
applies_to: "Any consumer (Claudony, devtown, or test module) that adds casehub-qhorus-slack-channel or a future optional qhorus module that ships JPA entities"
severity: important
refs:
  - runtime/src/main/resources/application.properties
  - slack-channel/src/test/resources/application.properties
violation_hint: "IllegalArgumentException: Unknown entity type 'io.casehub.qhorus.slack.SomeEntity' at augmentation or test startup — the entity class is on the classpath but Hibernate does not associate it with the qhorus PU"
created: 2026-06-18
---

The `qhorus` named Hibernate ORM persistence unit only scans packages declared in `quarkus.hibernate-orm.qhorus.packages` (currently `io.casehub.qhorus.runtime,io.casehub.ledger.runtime`). Optional qhorus modules that ship JPA entities in their own packages (e.g. `casehub-qhorus-slack-channel` puts entities in `io.casehub.qhorus.slack`) are excluded by default even though the jar is indexed by Jandex. Consumers must explicitly append the module's package to `quarkus.hibernate-orm.qhorus.packages` — either in their production `application.properties` (for production use) or in `src/test/resources/application.properties` (for test-only modules). The canonical reference configuration is in `slack-channel/src/test/resources/application.properties`.
