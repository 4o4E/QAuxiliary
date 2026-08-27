plugins {
    id("com.android.library")
}

group = "io.github.qauxv"
version = "1.0.0"

android {
    namespace = "io.github.qauxv.chainloader.api.emoticon"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

dependencies {
    compileOnly("androidx.annotation:annotation:1.10.0")
    testImplementation("junit:junit:4.13.2")
}
