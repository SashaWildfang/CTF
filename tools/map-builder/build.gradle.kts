plugins { java }
group = "com.sasha"
version = "1.0.0"
repositories { mavenCentral(); maven("https://repo.papermc.io/repository/maven-public/") }
dependencies { compileOnly("io.papermc.paper:paper-api:26.3.build.257-stable") }
java { toolchain.languageVersion = JavaLanguageVersion.of(25) }
tasks.compileJava { options.encoding = "UTF-8"; options.release = 25 }
