Summary:          Executor for Drove Container Orchestrator System
Name:             drove-executor
Version:          VERSIONSTR
Release:          RELEASESTR%{?dist}
URL:              https://github.com/PhonePe/drove
License:          Internal
Group:            Applications/System
Vendor:           PhonePe Private Limited
Distribution:     el9

BuildRequires: systemd-devel

Requires: systemd,podman,podman-docker,numactl
Requires: (java-17-openjdk-headless)
Requires(pre): systemd-rpm-macros
Requires(pre): /usr/sbin/useradd /usr/sbin/groupadd

%description
Executor for Drove Container Orchestrator System

%install
mkdir -p %{buildroot}%{_bindir} \
         %{buildroot}%{_sysconfdir} \
         %{buildroot}%{_unitdir} \
         %{buildroot}%{_libdir}/java \
         %{buildroot}%{_var}/log/drove/drove-executor
chmod 555  %{buildroot}%{_bindir} %{buildroot}%{_libdir} %{buildroot}/usr/lib
chmod 755 %{buildroot}/usr
cp usr/share/java/*.jar %{buildroot}%{_libdir}/java/
cp etc/drove-executor.yml  %{buildroot}%{_sysconfdir}/
cp etc/drove-executor.jvm.conf  %{buildroot}%{_sysconfdir}/
cp lib/systemd/system/drove-executor.service  %{buildroot}%{_unitdir}/
cp usr/bin/* %{buildroot}%{_bindir}/
pushd ..
rm -rf %{buildroot}/%{name}-%{version}
popd


install -vdm755 %{buildroot}%{_presetdir}
echo "disable drove-executor.service" > %{buildroot}%{_presetdir}/50-drove-executor.preset
install -p -D -m 0644 drove.sysusers %{buildroot}%{_sysusersdir}/drove.sysusers

%pre
%sysusers_create_compat drove.sysusers

%post
%{_sbindir}/ldconfig
%systemd_post drove-executor.service

%preun
%systemd_preun drove-executor.service

%postun
%systemd_postun_with_restart drove-executor.service
/sbin/ldconfig

%files
%defattr(-,root,root)
%attr(0755,drove,drove) %{_var}/log/drove
%attr(0755,drove,drove) %{_bindir}/drove-executor
%attr(0755,drove,drove) %{_libdir}/java/drove-executor.jar
%attr(0600,drove,drove) %{_sysconfdir}/drove-executor.yml
%attr(0600,drove,drove) %{_sysconfdir}/drove-executor.jvm.conf
%config(noreplace) %{_sysconfdir}/drove-executor.yml
%config(noreplace) %{_sysconfdir}/drove-executor.jvm.conf
%{_unitdir}/drove-executor.service
%{_presetdir}/50-drove-executor.preset
%{_sysusersdir}/drove.sysusers
%{_prefix}/*
%dir %{_var}/log/drove/drove-executor

%changelog
* Tue May 14 2024 Vishnu Naini <vishnu.naini@phonepe.com> VERSIONSTR-RELEASESTR
- Build for EL9
