# System setup for running drove with rootless podman

> *Note*: podman support is experimental at this point of time. Additionally, Please read through the following notes on
> rootless podman on [podman github](https://github.com/containers/podman/blob/main/rootless.md)

## Create userspace socket

```commandline
systemctl --user enable --now podman.socket
```

## Enable cpu and cpuset delegate

Check using:

```commandline
cat "/sys/fs/cgroup/user.slice/user-$(id -u).slice/user@$(id -u).service/cgroup.controllers"
```

if it shows only `memory pids` do the following:

```commandline
sudo su
vim /etc/systemd/system/user@.service.d/delegate.conf
```

add the following content.

```text
[Service]
Delegate=memory pids cpu cpuset
```

Logout and login again.

## Executor config setup

Use the following option in executor YAML config to point drove to podman rootless socket

```yaml
options:
  # other options
  dockerSocketPath: /path/to/podman.sock
```
