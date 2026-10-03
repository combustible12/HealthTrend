plugins { id("com.android.application"); id("org.jetbrains.kotlin.android"); id("org.jetbrains.kotlin.plugin.compose") }
android {
 namespace="com.combustible12.healthtrend"; compileSdk=35
 defaultConfig { applicationId="com.combustible12.healthtrend"; minSdk=26; targetSdk=35; versionCode=(System.getenv("HEALTHTREND_VERSION_CODE") ?: System.getenv("GITHUB_RUN_NUMBER") ?: "13").toInt(); versionName=System.getenv("HEALTHTREND_VERSION_NAME") ?: "0.2.${System.getenv("GITHUB_RUN_NUMBER") ?: "13"}"; testInstrumentationRunner="androidx.test.runner.AndroidJUnitRunner" }
 val stableKey=System.getenv("HEALTHTREND_KEYSTORE_PATH")
 if(!stableKey.isNullOrBlank()) {
  signingConfigs.create("healthtrend") { storeFile=file(stableKey);storePassword=System.getenv("HEALTHTREND_STORE_PASSWORD");keyAlias=System.getenv("HEALTHTREND_KEY_ALIAS");keyPassword=System.getenv("HEALTHTREND_KEY_PASSWORD") }
  buildTypes.getByName("debug").signingConfig=signingConfigs.getByName("healthtrend")
  buildTypes.getByName("release").signingConfig=signingConfigs.getByName("healthtrend")
 }
 compileOptions { sourceCompatibility=JavaVersion.VERSION_17; targetCompatibility=JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget="17" }
 buildFeatures { compose=true; buildConfig=true }
}
dependencies {
 implementation(platform("androidx.compose:compose-bom:2025.01.00"))
 androidTestImplementation(platform("androidx.compose:compose-bom:2025.01.00"))
 implementation("androidx.activity:activity-compose:1.10.0")
 implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
 implementation("androidx.core:core-ktx:1.15.0")
 implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
 testImplementation("junit:junit:4.13.2")
 androidTestImplementation("androidx.test.ext:junit:1.2.1")
 androidTestImplementation("androidx.test:runner:1.6.2")
 androidTestImplementation("androidx.test.espresso:espresso-intents:3.6.1")
 androidTestImplementation("androidx.compose.ui:ui-test-junit4")
 debugImplementation("androidx.compose.ui:ui-test-manifest")
 implementation("androidx.compose.material3:material3")
 implementation("androidx.compose.material:material-icons-extended")
 implementation("com.google.mlkit:text-recognition-chinese:16.0.1")
}
