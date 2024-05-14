Summary:          Controller for Drove Container Orchestrator System
Name:             drove-controller
Version:          VERSIONSTR
Release:          RELEASESTR%{?dist}
URL:              https://github.com/PhonePe/drove
License:          Internal
Group:            Applications/System
Vendor:           PhonePe Private Limited
Distribution:     el9

BuildRequires: systemd-devel

Requires: systemd,zookeeper
Requires: (java-17-openjdk-headless)
Requires(pre): systemd-rpm-macros
Requires(pre): /usr/sbin/useradd /usr/sbin/groupadd

%description
Controller for Drove Container Orchestrator System

%install
mkdir -p %{buildroot}%{_bindir} \
         %{buildroot}%{_sysconfdir} \
         %{buildroot}%{_unitdir} \
         %{buildroot}%{_libdir}/java \
         %{buildroot}%{_var}/log/drove/drove-controller
chmod 555  %{buildroot}%{_bindir} %{buildroot}%{_libdir} %{buildroot}/usr/lib
chmod 755 %{buildroot}/usr
cp usr/share/java/*.jar %{buildroot}%{_libdir}/java/
cp etc/drove-controller.yml  %{buildroot}%{_sysconfdir}/
cp etc/drove-controller.jvm.conf  %{buildroot}%{_sysconfdir}/
cp lib/systemd/system/drove-controller.service  %{buildroot}%{_unitdir}/
cp usr/bin/* %{buildroot}%{_bindir}/
pushd ..
rm -rf %{buildroot}/%{name}-%{version}
popd


install -vdm755 %{buildroot}%{_presetdir}
echo "disable drove-controller.service" > %{buildroot}%{_presetdir}/50-drove-controller.preset
install -p -D -m 0644 drove.sysusers %{buildroot}%{_sysusersdir}/drove.sysusers

%pre
%sysusers_create_compat drove.sysusers

%post
%{_sbindir}/ldconfig
%systemd_post drove-controller.service

%preun
%systemd_preun drove-controller.service

%postun
%systemd_postun_with_restart drove-controller.service
/sbin/ldconfig

%files
%defattr(-,root,root)
%attr(0755,drove,drove) %{_var}/log/drove
%attr(0755,drove,drove) %{_bindir}/drove-controller
%attr(0755,drove,drove) %{_libdir}/java/drove-controller.jar
%attr(0600,drove,drove) %{_sysconfdir}/drove-controller.yml
%attr(0600,drove,drove) %{_sysconfdir}/drove-controller.jvm.conf
%config(noreplace) %{_sysconfdir}/drove-controller.yml
%config(noreplace) %{_sysconfdir}/drove-controller.jvm.conf
%{_unitdir}/drove-controller.service
%{_presetdir}/50-drove-controller.preset
%{_sysusersdir}/drove.sysusers
%dir %{_var}/log/drove/drove-controller

%changelog
* Tue May 14 2024 Vishnu Naini <vishnu.naini@phonepe.com> VERSIONSTR-RELEASESTR
- Build for EL9
