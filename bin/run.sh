#!/bin/bash
# 指定 Java 路径
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk
export PATH=$JAVA_HOME/bin:$PATH

# 使用项目自带的 mvnw 编译并运行
./mvnw clean package -DskipTests
JAR_FILE=$(ls -t target/unidbg-boot-server-*.jar 2>/dev/null | head -1)
if [ -z "$JAR_FILE" ]; then
    echo "[$(date)] 错误: 未找到 JAR 文件 (target/unidbg-boot-server-*.jar)"
    exit 1
fi
echo "[$(date)] 使用 JAR: $JAR_FILE"
java -jar "$JAR_FILE"
