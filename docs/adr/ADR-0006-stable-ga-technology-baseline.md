# ADR-0006：正式 GA 技术基线

## 状态

已接受

## 背景

平台需要使用最新框架能力，但预览版本和任意覆盖传递依赖会破坏构建与支持的可预测性。

## 决策

固定 JDK 26.0.2、Maven 3.9.16、Spring Boot 4.1.0、Spring AI 2.0.0、Sa-Token 1.45.0、MyBatis-Plus 3.5.17、Hutool 5.8.47、Spring Modulith 2.1.0。禁止里程碑、候选、快照、JDK 预览/孵化 API 和未经批准的 BOM 依赖覆盖。

## 影响

- 必须配置 Maven Wrapper、Enforcer、SBOM、SCA 和依赖收敛。
- 非 LTS JDK 每半年需要一个兼容验证工作包。
- 依赖升级必须是有证据的变更，不能顺手修改。
