#!/bin/bash
# 自动探测 Java 路径；如需覆盖可预先设置 JAVA_HOME 环境变量
if [ -z "$JAVA_HOME" ] && command -v java >/dev/null 2>&1; then
    JAVA_HOME=$(dirname "$(dirname "$(readlink -f "$(command -v java)")")")
fi
if [ -n "$JAVA_HOME" ]; then
    export PATH="$JAVA_HOME/bin:$PATH"
fi
if ! command -v java >/dev/null 2>&1; then
    echo "[$(date)] 错误: 未找到 java，请安装 JDK 17 或设置 JAVA_HOME 环境变量"
    exit 1
fi

# 使用项目自带的 mvnw 编译并运行
./mvnw clean package -DskipTests
JAR_FILE=$(ls -t target/unidbg-boot-server-*.jar 2>/dev/null | head -1)
if [ -z "$JAR_FILE" ]; then
    echo "[$(date)] 错误: 未找到 JAR 文件 (target/unidbg-boot-server-*.jar)"
    exit 1
fi
echo "[$(date)] 使用 JAR: $JAR_FILE"
java -jar "$JAR_FILE"
