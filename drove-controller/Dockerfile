FROM ubuntu:jammy

USER root
RUN apt-get update && apt-get install -y openjdk-17-jre-headless

EXPOSE 4000
EXPOSE 4001

ARG VERSION

ADD configs/docker.yml config.yml
ADD target/drove-controller-$VERSION.jar drove-controller.jar
ADD entrypoint.sh entrypoint.sh

ENTRYPOINT ["bash", "./entrypoint.sh"]

