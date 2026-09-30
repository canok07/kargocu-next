# Kargo Tycoon

Fresh Android game project. Old Kargocu/Courier Rush sources are read-only references and are not the implementation base.

- `:game`: Android-independent Kotlin simulation, catalog and deterministic tests.
- `:app`: Android storage, application store, screen ViewModels and original Compose UI.
- Fixed dependencies are managed in `gradle/libs.versions.toml`.
- JVM bytecode targets Java 17. Use a supported modern runtime; this Windows setup uses Android Studio JBR 25.

Milestones: discovery → foundation → domain → engine → fleet → drivers → parallel deliveries → persistence → original UI → tutorial → Android validation → polish.

The foundation welcome screen is a launch/build baseline; gameplay is implemented in later milestones.
