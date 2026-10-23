FROM ubuntu:noble

USER root
RUN apt-get update && apt-get install -y --no-install-recommends openjdk-17-jre-headless \
    && apt-get purge -y shared-mime-info \
    && apt-get autoremove -y \
    && rm -rf /var/lib/apt/lists/* /var/cache/apt/* /usr/share/doc/* /usr/share/man/* \
        /usr/share/locale/* /usr/share/info/* /usr/share/perl5/* /usr/lib/x86_64-linux-gnu/gconv \
    && find /usr/bin -maxdepth 1 -type f -perm -4000 -delete

EXPOSE 4000
EXPOSE 4001

ARG VERSION

COPY configs/docker.yml config.yml
COPY target/drove-controller-$VERSION.jar drove-controller.jar
COPY entrypoint.sh entrypoint.sh

ENTRYPOINT ["bash", "./entrypoint.sh"]
