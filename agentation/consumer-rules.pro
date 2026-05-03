# Consumer ProGuard rules for agentation library.
# Keep public API surface stable for consumers.
-keep class dev.agentation.Agentation { *; }
-keep class dev.agentation.AgentationConfig { *; }
-keep class dev.agentation.model.** { *; }
