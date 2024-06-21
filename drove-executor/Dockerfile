FROM ubuntu:jammy

USER root
RUN apt-get update && apt-get install -y openjdk-17-jre-headless numactl

EXPOSE 3000
EXPOSE 3001

ARG VERSION

ADD configs/docker.yml config.yml
ADD target/drove-executor-$VERSION.jar drove-executor.jar
ADD entrypoint.sh entrypoint.sh

ENTRYPOINT ["bash", "./entrypoint.sh"]

