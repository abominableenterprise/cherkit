kotlin/android: fixed

- Kotlin/Android artifacts now target Java 17 bytecode (class version 61), so apps whose JVM/Robolectric unit tests run on JDK 17 can load them; 0.9.0 artifacts required JDK 21.
