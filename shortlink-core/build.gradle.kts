plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencies {
    implementation(project(":shortlink-common"))
    implementation(project(":shortlink-cache"))
    implementation(project(":shortlink-analytics"))
    implementation(project(":shortlink-openapi"))
    implementation(project(":shortlink-admin"))

    implementation("org.springframework.boot:spring-boot-starter-web") {
        exclude(group = "org.springframework.boot", module = "spring-boot-starter-tomcat")
    }
    implementation("org.springframework.boot:spring-boot-starter-undertow")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("com.baomidou:mybatis-plus-spring-boot3-starter:3.5.9")
    implementation("com.mysql:mysql-connector-j")

    // Micrometer + Prometheus
    implementation("io.micrometer:micrometer-registry-prometheus")

    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
