# مرجع عملیاتی دستورها و عیب‌یابی زیرساخت IT

> نسخهٔ آموزشی و عملیاتی MindUnlock — دستورهای این سند عمدتاً فقط‌خواندنی و تشخیصی‌اند. قبل از اجرای هر فرمان تغییردهنده، از پیکربندی Backup بگیرید، پنجرهٔ نگهداری تعیین کنید و اثر آن را بسنجید. نام Interface، IP، VLAN، Domain و مسیرها را با مقادیر واقعی محیط جایگزین کنید.

## روش استاندارد عیب‌یابی

1. محدودهٔ خرابی را مشخص کنید: یک کاربر، یک VLAN، یک سایت یا کل سرویس.
2. لایه‌ای بررسی کنید: برق/لینک، VLAN و IP، Gateway و Routing، DNS، پورت و سرویس، احراز هویت، برنامه.
3. از نزدیک‌ترین نقطه به دورترین نقطه تست کنید؛ نتیجه و زمان هر تست را ثبت کنید.
4. قبل و بعد از هر تغییر خروجی دستورها و لاگ‌ها را ذخیره کنید.
5. با یک تست موفق نهایی از سمت کلاینت و سرور، رفع مشکل را تأیید کنید.

## 1) ابزارهای پایهٔ شبکه

### Windows
```powershell
ipconfig /all
ipconfig /displaydns
ipconfig /flushdns
ipconfig /release
ipconfig /renew
ping 192.0.2.1
tracert 1.1.1.1
pathping 1.1.1.1
nslookup example.com
nslookup -type=SRV _ldap._tcp.dc._msdcs.example.local
Test-NetConnection 192.0.2.10 -Port 445
Test-NetConnection dc01.example.local -Port 53
Get-NetIPConfiguration
Get-NetAdapter
Get-NetRoute
Get-DnsClientServerAddress
arp -a
route print
netstat -ano
```
- `ipconfig /all`: آدرس، Gateway، DNS، DHCP و Lease.
- `Test-NetConnection`: تست TCP به پورت مشخص؛ Ping به‌تنهایی بازبودن پورت را ثابت نمی‌کند.
- `nslookup -type=SRV`: بررسی رکوردهای کشف Domain Controller.

### Linux
```bash
ip -br address
ip route
ip neigh
ip link
ping -c 4 192.0.2.1
tracepath 1.1.1.1
traceroute 1.1.1.1
dig example.com A
dig example.com MX
dig -x 192.0.2.10
resolvectl status
getent hosts server01
ss -tulpn
ss -s
curl -Iv https://example.com
nc -vz 192.0.2.10 443
mtr -rw 1.1.1.1
```
- برای DNS از `dig` و `resolvectl` استفاده کنید؛ فایل `/etc/resolv.conf` ممکن است توسط NetworkManager یا systemd-resolved مدیریت شود.
- `ss -tulpn` پورت‌های Listen و پردازش مالک آن‌ها را نشان می‌دهد؛ برای دیدن نام بعضی پردازش‌ها دسترسی root لازم است.

## 2) Cisco IOS / IOS-XE و سوئیچ Cisco

### مشاهدهٔ وضعیت و سلامت
```text
show version
show inventory
show clock
show environment all
show interfaces status
show interfaces description
show interfaces counters errors
show interfaces GigabitEthernet1/0/1
show ip interface brief
show ip route
show arp
show mac address-table
show logging
show processes cpu sorted
show memory statistics
show spanning-tree summary
show cdp neighbors detail
show lldp neighbors detail
show etherchannel summary
show power inline
```

### VLAN و Trunk
```text
show vlan brief
show vlan id 20
show interfaces trunk
show interfaces switchport
show spanning-tree vlan 20
show mac address-table vlan 20
show mac address-table interface GigabitEthernet1/0/1
```
- اگر کلاینت IP نمی‌گیرد: VLAN پورت، وضعیت Access/Trunk، VLAN مجاز روی Trunk، DHCP relay و مسیر تا DHCP Server را بررسی کنید.
- اگر ارتباط بین VLANها قطع است: SVI، وضعیت `ip routing`، Route، ACL و Gateway کلاینت را بررسی کنید.
- خطاهای CRC، input errors، drops و interface flaps می‌توانند نشانهٔ کابل، ماژول، Duplex/Speed یا مشکل سمت مقابل باشند.

### تست‌های تکمیلی
```text
show ip interface vlan 20
show access-lists
show ip access-lists
show ip dhcp snooping
show port-security
show port-security interface GigabitEthernet1/0/1
show errdisable recovery
show interfaces status err-disabled
ping 192.0.2.1 source vlan 20
traceroute 192.0.2.10
```
> دستورات `configure terminal`، `shutdown/no shutdown`، تغییر VLAN و پاک‌کردن جدول‌ها تغییردهنده‌اند و در این بخش به‌عنوان تست روتین پیشنهاد نمی‌شوند.

## 3) MikroTik RouterOS

### RouterOS v7 — بررسی
```routeros
/system resource print
/system package print
/system clock print
/log print
/interface print detail
/interface ethernet print stats
/interface bridge print
/interface bridge port print
/interface bridge vlan print
/interface vlan print
/ip address print detail
/ip route print detail
/ip arp print
/ip dns print
/ip dns cache print
/ip dhcp-server print detail
/ip dhcp-server lease print
/ip pool print
/ip firewall filter print stats
/ip firewall nat print stats
/ip firewall connection print count-only
/tool ping 1.1.1.1 count=4
/tool traceroute 1.1.1.1
/tool torch interface=ether1
/tool profile
```
- `/tool torch` برای مشاهدهٔ ترافیک زنده است؛ روی لینک پرترافیک با احتیاط استفاده شود.
- در VLAN filtering، تطبیق Bridge VLAN، PVID پورت، tagged/untagged و عضویت CPU/Bridge را بررسی کنید.
- در DHCP، Pool، Network، Gateway، DNS، Lease و وضعیت Interface را با هم بررسی کنید.

### خروجی پیکربندی برای مستندسازی
```routeros
/export terse
/interface print
/ip firewall filter print detail
/ip firewall nat print detail
```
> خروجی Export ممکن است IP عمومی، نام کاربری یا دادهٔ حساس داشته باشد؛ پیش از اشتراک‌گذاری پاک‌سازی شود. فرمان‌های Reset، حذف Rule و تغییر Firewall بدون Backup اجرا نشوند.

## 4) VLAN، Subnet، Routing و DHCP Relay

چک‌لیست هر VLAN:
- VLAN ID و نام، Subnet/CIDR، Gateway، DHCP Scope و DNS مشخص باشد.
- پورت Access فقط عضو VLAN موردنیاز باشد.
- Trunk فقط VLANهای لازم را حمل کند و Native VLAN در دو سمت هماهنگ باشد.
- Gateway روی SVI یا Firewall وجود داشته باشد.
- ACL/Firewall بین VLANها فقط ترافیک موردنیاز را اجازه دهد.
- DHCP Relay/IP Helper برای Scope متمرکز تنظیم شده باشد.
- برای هر VLAN یک کلاینت آزمایشی با IP، Gateway، DNS و دسترسی مورد انتظار تست شود.

فرمان‌های تشخیصی: Cisco `show vlan brief`, `show interfaces trunk`, `show ip route`; MikroTik `/interface bridge vlan print`, `/interface bridge port print`, `/ip dhcp-server lease print`; Windows `ipconfig /all`, `route print`.

## 5) FortiGate

### وضعیت، Interface، Route و Session
```text
get system status
get system performance status
get system interface
show system interface
get router info routing-table all
get router info routing-table details 192.0.2.10
diagnose ip address list
diagnose netlink interface list
get system arp
diagnose sys session stat
diagnose sys top 1 20
diagnose hardware sysinfo memory
diagnose hardware sysinfo conserve
```

### VPN، Policy، DNS و DHCP
```text
get vpn ipsec tunnel summary
diagnose vpn ike gateway list
diagnose vpn tunnel list
diagnose firewall iprope list 100004
show firewall policy
show firewall address
show system dhcp server
diagnose ip dhcp server list
diagnose test application dnsproxy 6
```

### Packet capture / flow debug
```text
diagnose sniffer packet any 'host 192.0.2.25 and port 53' 4 20 l
diagnose sniffer packet any 'host 192.0.2.25 and port 443' 4 20 l
diagnose debug reset
diagnose debug flow filter addr 192.0.2.25
diagnose debug flow show function-name enable
diagnose debug flow trace start 100
diagnose debug enable
diagnose debug disable
```
- Flow debug می‌تواند حجم زیادی Log تولید کند؛ فقط برای بازهٔ کوتاه و با فیلتر دقیق اجرا شود و در پایان Debug خاموش شود.
- برای قطع ارتباط، مسیر رفت و برگشت، Policy match، NAT، Route، UTM profile، SSL inspection و Session را بررسی کنید.

## 6) Linux Server

### سیستم، منابع و سرویس‌ها
```bash
hostnamectl
uname -a
uptime
free -h
df -hT
lsblk -f
lscpu
top
ps aux --sort=-%cpu | head
systemctl --failed
systemctl status ssh
journalctl -p warning -b
journalctl -u ssh --since '1 hour ago'
dmesg -T | tail -100
```

### سرویس، پورت، Firewall و Storage
```bash
systemctl list-units --type=service --state=running
systemctl list-unit-files
ss -lntup
sudo nft list ruleset
sudo ufw status verbose
findmnt
iostat -xz 1 5
vmstat 1 5
du -xhd1 /var 2>/dev/null | sort -h
```
- قبل از Restart سرویس، `systemctl status` و لاگ همان سرویس را بخوانید.
- در فضای دیسک، فقط فایل‌های بزرگ را شناسایی کنید؛ حذف Log/Database بدون بررسی Rotation و Backup ممنوع است.

## 7) Windows Server

### وضعیت سیستم، سرویس و Event Log
```powershell
systeminfo
Get-ComputerInfo
Get-Service | Where-Object Status -ne 'Running'
Get-WinEvent -LogName System -MaxEvents 100
Get-WinEvent -LogName Application -MaxEvents 100
Get-WinEvent -FilterHashtable @{LogName='System'; Level=2; StartTime=(Get-Date).AddHours(-4)}
Get-Process | Sort-Object CPU -Descending | Select-Object -First 15
Get-Volume
Get-Disk
Get-NetAdapter
Get-SmbServerConfiguration
```

### سرویس‌های کلیدی
```powershell
Get-Service DNS,DHCPServer,NTDS,Netlogon,DFSR,lanmanserver
Get-WindowsFeature
Get-SmbShare
Get-SmbSession
Get-SmbOpenFile
```
- نام سرویس‌ها در نسخه‌ها و Roleهای مختلف ممکن است تفاوت داشته باشد؛ ابتدا با `Get-Service` موجودبودن آن‌ها را بررسی کنید.

## 8) Active Directory و Domain Controller

### سلامت Domain و Replication
```powershell
dcdiag /v
dcdiag /test:dns /v
repadmin /replsummary
repadmin /showrepl
repadmin /queue
nltest /dsgetdc:example.local
nltest /dclist:example.local
nltest /sc_verify:example.local
Get-ADDomain
Get-ADForest
Get-ADDomainController -Filter *
Get-ADReplicationFailure -Scope Forest
Get-ADReplicationPartnerMetadata -Target * -Scope Forest
```

### کاربران، گروه‌ها و کامپیوترها
```powershell
Get-ADUser -Filter * -SearchBase 'OU=Users,DC=example,DC=local' -Properties Enabled,LastLogonDate
Get-ADUser username -Properties *
Get-ADGroupMember 'Domain Admins'
Get-ADComputer -Filter * -Properties OperatingSystem
Search-ADAccount -LockedOut
Search-ADAccount -PasswordExpired
Get-ADOrganizationalUnit -Filter *
```
- پیش از Unlock، Reset Password یا تغییر عضویت گروه، هویت درخواست‌کننده و مجوز را بررسی کنید.
- اگر ورود Domain مشکل دارد، DNS کلاینت، ساعت سیستم، SRV Record، دسترسی به DC و Secure Channel را بررسی کنید.

## 9) Group Policy

### مشاهده و عیب‌یابی سمت کلاینت
```powershell
gpupdate /target:computer /force
gpupdate /target:user /force
gpresult /r
gpresult /h "$env:TEMP\gpresult.html"
rsop.msc
Get-GPResultantSetOfPolicy -ReportType Html -Path "$env:TEMP\rsop.html"
Get-WinEvent -LogName 'Microsoft-Windows-GroupPolicy/Operational' -MaxEvents 100
```
### مشاهده GPO از PowerShell
```powershell
Get-GPO -All
Get-GPO -Name 'Default Domain Policy' -ReportType Html -Path "$env:TEMP\DefaultDomainPolicy.html"
Get-GPInheritance -Target 'OU=Workstations,DC=example,DC=local'
Get-GPPermission -Name 'Workstation Baseline' -All
```
- ترتیب بررسی: Scope/Link، Security Filtering، WMI Filter، Inheritance/Enforced، وضعیت SYSVOL/NETLOGON و Event Log.
- `gpupdate /force` Policy را دوباره اعمال می‌کند؛ برای عیب‌یابی معمول مناسب است اما در محیط حساس باید اثر آن سنجیده شود.

## 10) DNS Server و DHCP Server در Windows

### DNS
```powershell
Get-DnsServerZone
Get-DnsServerResourceRecord -ZoneName 'example.local'
Get-DnsServerForwarder
Get-DnsServerDiagnostics
Resolve-DnsName dc01.example.local
Resolve-DnsName -Type SRV _ldap._tcp.dc._msdcs.example.local
Clear-DnsServerCache -ComputerName dns01
```
- `Clear-DnsServerCache` کش DNS سرور را پاک می‌کند؛ فقط در صورت نیاز و با اطلاع از اثر آن اجرا شود.
- رکوردهای A/PTR، CNAME، SRV، Forwarder، Zone Replication و Dynamic Updates بررسی شوند.

### DHCP
```powershell
Get-DhcpServerv4Scope -ComputerName dhcp01
Get-DhcpServerv4ScopeStatistics -ComputerName dhcp01
Get-DhcpServerv4Lease -ComputerName dhcp01 -ScopeId 192.0.2.0
Get-DhcpServerv4OptionValue -ComputerName dhcp01
Get-DhcpServerInDC
Get-Service DHCPServer
```
- Scope exhaustion، Exclusion/Reservation، Option 003 Router، Option 006 DNS، Conflict Detection و DHCP Relay را بررسی کنید.

## 11) پوشهٔ اشتراکی، SMB و Permission

### Windows
```powershell
Get-SmbShare
Get-SmbShareAccess -Name 'Shared'
Get-SmbSession
Get-SmbOpenFile
Get-SmbClientConfiguration
Test-NetConnection files01.example.local -Port 445
Get-Acl '\\files01\Shared' | Format-List
whoami /all
icacls '\\files01\Shared'
cmdkey /list
net use
```
- دسترسی مؤثر SMB حاصل ترکیب Share Permission و NTFS Permission است؛ هر دو را بررسی کنید.
- برای کاربر آزمایشی، دسترسی Read/Modify را جداگانه تست کنید؛ از اعطای Everyone/Full Control به‌عنوان راه‌حل استفاده نکنید.

### Linux Samba
```bash
testparm -s
smbclient -L //files01 -U username
smbclient //files01/share -U username
smbstatus
getfacl /srv/share
namei -l /srv/share
```

## 12) VMware ESXi / vSphere

### ESXi Shell / SSH
```shell
vmware -vl
esxcli system version get
esxcli hardware platform get
esxcli hardware cpu list
esxcli hardware memory get
esxcli network ip interface list
esxcli network ip interface ipv4 get
esxcli network ip route ipv4 list
esxcli network nic list
esxcli network vswitch standard list
esxcli network vswitch standard portgroup list
esxcli storage core adapter list
esxcli storage core path list
esxcli storage filesystem list
esxcli vm process list
esxcli system time get
/etc/init.d/hostd status
/etc/init.d/vpxa status
```
- قبل از Restart کردن Management Agent، دسترسی مدیریتی و اثر روی vCenter/عملیات جاری را ارزیابی کنید.
- Datastore latency، مسیرهای Storage، NIC link، vSwitch/Port Group، VLAN ID و VMkernel interface بررسی شوند.

## 13) سرورهای HPE / HP، iLO و RAID

### Linux روی سرور HPE
```bash
sudo dmidecode -t system
sudo dmidecode -t memory
sudo lspci
sudo lsscsi
sudo lsblk -o NAME,SIZE,TYPE,MODEL,SERIAL
sudo smartctl --scan
sudo smartctl -a /dev/sdX
sudo ssacli ctrl all show status
sudo ssacli ctrl all show config
sudo ssacli ctrl slot=0 pd all show
sudo ssacli ctrl slot=0 ld all show
sudo hponcfg -w /tmp/ilo-config.xml
```
- نام ابزارها و مسیر کنترلر به مدل سرور و نسخهٔ OS/Agent وابسته است؛ `ssacli` ممکن است نصب نباشد.
- `smartctl` برای همهٔ Logical Driveهای Smart Array جایگزین ابزار رسمی کنترلر نیست.
- فایل iLO XML می‌تواند اطلاعات حساس داشته باشد؛ آن را منتشر نکنید.

### iLO
- از Web UI یا ابزار رسمی HPE iLOrest برای مشاهدهٔ Health، Inventory، Firmware، Power و Integrated Management Log استفاده کنید.
- قبل از Firmware update، مدل دقیق سرور، نسل iLO، سازگاری Firmware، Backup و پنجرهٔ نگهداری را بررسی کنید.
- خاموش/روشن‌کردن، Reset iLO، تغییر BIOS و RAID Configuration عملیات تغییردهنده و پرریسک‌اند.

## 14) Veeam Backup & Replication

### PowerShell (Veeam PowerShell Module)
```powershell
Get-Module -ListAvailable Veeam*
Get-Service | Where-Object {$_.Name -match 'Veeam'}
Get-VBRJob
Get-VBRJob | Select-Object Name,JobType,IsScheduleEnabled
Get-VBRBackup
Get-VBRRestorePoint
Get-VBRRepository
Get-VBRServer
Get-VBRSession
```
- در برخی نسخه‌ها نام Cmdlet یا Propertyها تفاوت دارد؛ ابتدا `Get-Command -Module Veeam*` و `Get-Help <Cmdlet> -Full` را اجرا کنید.
- موفقیت Job به‌تنهایی کافی نیست: Restore Point، Repository Capacity، Retention، SOBR/Copy Job و آخرین زمان Backup را بررسی کنید.
- آزمون Restore باید دوره‌ای و در شبکه/محیط ایزوله انجام شود؛ فایل Backup را صرفاً به‌دلیل وجود داشتن، قابل‌بازیابی فرض نکنید.

## 15) Issabel / Asterisk / VoIP / SIP

### Asterisk CLI
```bash
asterisk -rvvv
core show version
core show uptime
core show channels
core show channels verbose
core show channeltypes
pjsip show endpoints
pjsip show registrations
pjsip show contacts
pjsip show transports
pjsip show aors
sip show peers
sip show registry
dialplan show
module show
queue show
core show translation
```
- در نسخه‌های جدید معمولاً PJSIP رایج است؛ `sip show peers` فقط در نصب‌های دارای chan_sip کاربرد دارد.
- برای تماس ناموفق، Registration، Contact، Endpoint/AOR، Dialplan، Codec، NAT، Firewall و مسیر RTP بررسی شوند.

### لاگ و شبکه
```bash
journalctl -u asterisk --since '30 minutes ago'
tail -f /var/log/asterisk/full
ss -lunp | grep -E '5060|5061|10000'
tcpdump -ni any 'port 5060 or port 5061'
tcpdump -ni any 'udp portrange 10000-20000'
```
- SIP معمولاً روی 5060/5061 و RTP روی بازهٔ تعریف‌شده در تنظیمات Asterisk است؛ پورت‌های واقعی را از پیکربندی بخوانید.
- Capture حاوی شماره تلفن، IP و گاهی دادهٔ حساس مکالمه است؛ فقط با مجوز و حداقل زمان لازم ضبط شود.

## 16) FXO، خطوط شهری و Gateway تلفنی

چک‌لیست عیب‌یابی:
1. وضعیت فیزیکی خط شهری، کابل، پورت FXO و چراغ‌های Gateway.
2. تشخیص اشغال/آزاد بودن خط و وجود ولتاژ/سیگنال توسط ابزار مجاز مخابراتی.
3. وضعیت Port/Trunk در Gateway و Registration/Connectivity آن با PBX.
4. Dial Pattern و Outbound Route در Issabel/Asterisk.
5. مسیر صدای دوطرفه، تشخیص Busy، Caller ID و Disconnect Supervision.
6. تست تماس ورودی و خروجی با شمارهٔ آزمایشی و ثبت زمان/نتیجه.

دستورهای مشاهده در Asterisk: `pjsip show endpoints`، `pjsip show contacts`، `dialplan show`، `core show channels` و مشاهدهٔ Log. تنظیم Impedance، Country Tone، Gain و Port Mode باید مطابق مدل Gateway و مشخصات اپراتور انجام شود؛ مقدار عمومی و یکسان برای همهٔ خطوط وجود ندارد.

## 17) Zero Client / Thin Client

از آنجا که فرمان‌ها به سازنده و پروتکل وابسته‌اند (HP Anyware/TERA، Dell/Wyse، IGEL، VMware Horizon و غیره)، ابتدا Vendor، Model، Firmware و پروتکل را ثبت کنید.

### تست‌های عمومی از کلاینت Windows
```powershell
ipconfig /all
ping <gateway-ip>
nslookup <connection-broker-fqdn>
Test-NetConnection <connection-broker-fqdn> -Port 443
Test-NetConnection <connection-broker-fqdn> -Port 4172
Test-NetConnection <connection-broker-fqdn> -Port 3389
Test-NetConnection <connection-broker-fqdn> -Port 8443
```
- این پورت‌ها نمونه‌اند و بسته به محصول، نسخه و پروتکل ممکن است متفاوت باشند؛ فقط پورت‌های مستندشدهٔ همان Vendor را تست کنید.
- DHCP، DNS، NTP، Gateway، VLAN، Certificate/Trust، Broker، Firmware و ظرفیت Session Host را بررسی کنید.
- در Zero Client واقعی ممکن است Shell عمومی وجود نداشته باشد؛ تست را از پنل مدیریتی یا ابزار سازنده انجام دهید.

## 18) حسابداری پارسیان و سپیدار

> این نرم‌افزارها رابط CLI عمومی و استانداردی برای عیب‌یابی همهٔ نسخه‌ها ندارند؛ بنابراین فرمان اختصاصی ساختگی ارائه نمی‌شود. عیب‌یابی از لایهٔ کلاینت، سرور برنامه، SQL، شبکه، مجوز و Backup انجام می‌شود.

### تست ارتباط کلاینت با سرور
```powershell
Resolve-DnsName <accounting-server>
Test-NetConnection <accounting-server> -Port 1433
Test-NetConnection <accounting-server> -Port <application-port>
Get-Service | Where-Object {$_.DisplayName -match 'SQL|Sepidar|Parsian'}
Get-WinEvent -LogName Application -MaxEvents 100
```
- پورت 1433 فقط پورت پیش‌فرض رایج SQL Server است؛ Instanceهای نام‌دار یا پورت سفارشی ممکن است متفاوت باشند.
- مسیر نصب، نسخهٔ کلاینت و سرور، SQL Instance، سرویس‌ها، دسترسی کاربر، فضای دیسک، DNS و Logهای رسمی نرم‌افزار بررسی شوند.
- قبل از تغییر Database، Repair، Upgrade یا Restore، Backup معتبر و تأیید مسئول مالی الزامی است.
- هرگز مستقیماً جداول مالی را با SQL دستی اصلاح یا حذف نکنید؛ از ابزار رسمی نرم‌افزار و پشتیبانی سازنده استفاده کنید.

## 19) Microsoft SQL Server — نصب، سرویس، اتصال و عیب‌یابی

این فصل برای SQL Server Database Engine، SSMS و محیط‌های سازمانی طراحی شده است. نسخه، Edition، نام Instance، روش احراز هویت و پورت واقعی را قبل از شروع ثبت کنید. بسیاری از نصب‌ها از Instance نام‌دار یا پورت پویا استفاده می‌کنند؛ بنابراین فرض همیشگی TCP 1433 اشتباه است.

### 19.1 شناسایی نسخه و Instance

در SSMS یک Query Window باز کنید و این پرس‌وجوهای فقط‌خواندنی را اجرا کنید:

```sql
SELECT
  @@SERVERNAME AS ServerName,
  SERVERPROPERTY('MachineName') AS MachineName,
  SERVERPROPERTY('InstanceName') AS InstanceName,
  SERVERPROPERTY('Edition') AS Edition,
  SERVERPROPERTY('ProductVersion') AS ProductVersion,
  SERVERPROPERTY('ProductLevel') AS ProductLevel,
  SERVERPROPERTY('ProductUpdateLevel') AS ProductUpdateLevel,
  SERVERPROPERTY('IsClustered') AS IsClustered;

SELECT name, state_desc, recovery_model_desc, compatibility_level
FROM sys.databases
ORDER BY name;
```

نسخهٔ نصب‌شده را با برنامهٔ به‌روزرسانی و پشتیبانی نرم‌افزار سازمانی تطبیق دهید. قبل از ارتقا، سازگاری نسخهٔ SQL، سیستم‌عامل، SSMS، درایورهای کلاینت و نرم‌افزار مالی را بررسی کنید.

### 19.2 سرویس‌ها و پورت در Windows

```powershell
Get-Service -Name 'MSSQLSERVER','SQLSERVERAGENT','SQLBrowser' -ErrorAction SilentlyContinue
Get-Service | Where-Object {$_.Name -like 'MSSQL$*' -or $_.Name -like 'SQLAgent$*'}
Get-CimInstance Win32_Service | Where-Object {$_.Name -match 'MSSQL|SQLAgent|SQLBrowser'} |
  Select-Object Name,State,StartMode,StartName,PathName
Get-NetTCPConnection -State Listen |
  Where-Object {$_.LocalPort -in 1433,1434} |
  Select-Object LocalAddress,LocalPort,OwningProcess
Get-Process -Id (Get-NetTCPConnection -LocalPort 1433 -State Listen -ErrorAction SilentlyContinue).OwningProcess
Test-NetConnection sql01.example.local -Port 1433
```

نکات:
- سرویس پیش‌فرض معمولاً `MSSQLSERVER` و Instance نام‌دار معمولاً `MSSQL$<InstanceName>` است.
- SQL Server Browser معمولاً برای کشف پورت Instanceهای نام‌دار استفاده می‌شود؛ UDP 1434 را فقط در صورت نیاز معماری باز کنید.
- اگر TCP/IP در SQL Server Configuration Manager غیرفعال باشد، اتصال TCP برقرار نمی‌شود؛ تغییر آن نیازمند برنامه‌ریزی و Restart سرویس است.
- پورت واقعی را در SQL Server Configuration Manager، بخش TCP/IP و IPAll بررسی کنید؛ سپس Firewall را محدود به مبدأهای مجاز کنید.

### 19.3 تشخیص خطای اتصال از کلاینت

```powershell
Resolve-DnsName sql01.example.local
Test-NetConnection sql01.example.local -Port 1433 -InformationLevel Detailed
sqlcmd -S tcp:sql01.example.local,1433 -E -Q "SELECT @@SERVERNAME, GETDATE();"
sqlcmd -S "sql01\ACCOUNTING" -E -Q "SELECT @@SERVERNAME;"
```

- خطای Name resolution: DNS، رکورد A و Suffix را بررسی کنید.
- خطای Timeout: مسیر، Firewall، Listener، پورت و TCP/IP را بررسی کنید.
- خطای Login failed: نوع Authentication، Login، وضعیت Login، مجوز Database و سیاست‌های سازمان را بررسی کنید.
- خطای Certificate/TLS: نام گواهی، زنجیرهٔ اعتماد، تاریخ انقضا و تنظیمات Encryption را بررسی کنید؛ Encryption را برای دور زدن خطا خاموش نکنید.
- ابزار `sqlcmd` ممکن است نصب نباشد؛ در این صورت از SSMS یا ابزار رسمی کلاینت استفاده کنید.

### 19.4 وضعیت Engine و خطاهای اخیر

```sql
SELECT sqlserver_start_time
FROM sys.dm_os_sys_info;

SELECT TOP (100)
  creation_time, total_worker_time, execution_count,
  total_elapsed_time, (total_elapsed_time / NULLIF(execution_count,0)) AS avg_elapsed_time,
  (total_logical_reads / NULLIF(execution_count,0)) AS avg_logical_reads,
  LEFT(t.text, 4000) AS query_text
FROM sys.dm_exec_query_stats AS qs
CROSS APPLY sys.dm_exec_sql_text(qs.sql_handle) AS t
ORDER BY total_elapsed_time DESC;

EXEC sys.xp_readerrorlog 0, 1, N'error';
```

دسترسی به بعضی DMVها و Error Log ممکن است به مجوزهای بالاتر مانند VIEW SERVER STATE یا نقش‌های مدیریتی نیاز داشته باشد. دسترسی اضافی را صرفاً برای راحتی اعطا نکنید.

### 19.5 Database، فایل‌ها و فضای دیسک

```sql
SELECT
  DB_NAME(database_id) AS database_name,
  type_desc, name, physical_name,
  size * 8.0 / 1024 AS size_mb
FROM sys.master_files
ORDER BY database_id, type_desc;

SELECT
  DB_NAME(database_id) AS database_name,
  SUM(size) * 8.0 / 1024 AS allocated_mb
FROM sys.master_files
GROUP BY database_id
ORDER BY allocated_mb DESC;

SELECT name, state_desc, user_access_desc, recovery_model_desc,
       page_verify_option_desc, is_read_only, is_auto_close_on
FROM sys.databases
ORDER BY name;
```

```powershell
Get-Volume | Sort-Object DriveLetter
Get-PSDrive -PSProvider FileSystem
Get-ChildItem 'D:\SQLData' -File -ErrorAction SilentlyContinue |
  Sort-Object Length -Descending | Select-Object -First 20 FullName,Length
```

فایل‌های MDF/NDF/LDF را دستی جابه‌جا، حذف یا Rename نکنید. پرشدن دیسک Data، Log، TempDB و Backup را جداگانه بررسی کنید. رشد فایل را با رشد واقعی داده، Autogrowth و ظرفیت Volume مقایسه کنید.

### 19.6 Blocking، Session و Deadlock

```sql
SELECT
  r.session_id, r.status, r.command, r.wait_type, r.wait_time,
  r.blocking_session_id, r.cpu_time, r.total_elapsed_time,
  DB_NAME(r.database_id) AS database_name,
  SUBSTRING(st.text, (r.statement_start_offset/2)+1,
    (CASE WHEN r.statement_end_offset = -1
      THEN DATALENGTH(st.text)
      ELSE r.statement_end_offset END - r.statement_start_offset)/2 + 1) AS statement_text
FROM sys.dm_exec_requests AS r
CROSS APPLY sys.dm_exec_sql_text(r.sql_handle) AS st
WHERE r.session_id <> @@SPID
ORDER BY r.wait_time DESC;

SELECT session_id, blocking_session_id, wait_type, wait_duration_ms,
       resource_description
FROM sys.dm_os_waiting_tasks
WHERE blocking_session_id IS NOT NULL;

SELECT name, value, value_in_use
FROM sys.configurations
WHERE name IN ('blocked process threshold (s)','max degree of parallelism',
               'cost threshold for parallelism');
```

- Blocking را با زمان، تراکنش باز، برنامهٔ مبدأ و Query درگیر تحلیل کنید؛ Kill کردن Session بدون شناخت تراکنش می‌تواند باعث Rollback طولانی یا از دست رفتن کار شود.
- برای Deadlock، Extended Events و گزارش رسمی SQL Server را بررسی کنید؛ Trace یا Capture دائمی و پرحجم را بدون سنجش اثر فعال نکنید.
- Wait Typeها باید در زمینهٔ بار کاری تفسیر شوند؛ یک Wait منفرد به‌تنهایی اثبات خرابی نیست.

### 19.7 Backup، Restore و اعتبارسنجی

```sql
SELECT
  d.name AS database_name,
  MAX(CASE WHEN b.type='D' THEN b.backup_finish_date END) AS last_full_backup,
  MAX(CASE WHEN b.type='I' THEN b.backup_finish_date END) AS last_diff_backup,
  MAX(CASE WHEN b.type='L' THEN b.backup_finish_date END) AS last_log_backup
FROM sys.databases AS d
LEFT JOIN msdb.dbo.backupset AS b ON b.database_name = d.name
GROUP BY d.name
ORDER BY d.name;

SELECT TOP (100)
  database_name, type, backup_start_date, backup_finish_date,
  backup_size, compressed_backup_size, is_copy_only
FROM msdb.dbo.backupset
ORDER BY backup_finish_date DESC;

SELECT TOP (100) *
FROM msdb.dbo.restorehistory
ORDER BY restore_date DESC;
```

اعتبارسنجی Backup:
```sql
RESTORE HEADERONLY FROM DISK = N'E:\SQLBackup\sample.bak';
RESTORE VERIFYONLY FROM DISK = N'E:\SQLBackup\sample.bak';
```

- `RESTORE VERIFYONLY` جایگزین Restore واقعی و آزمون صحت کاربردی Database نیست.
- Restore آزمایشی را با نام و مسیر Data/Log جداگانه در محیط ایزوله انجام دهید؛ روی Database عملیاتی Restore نکنید.
- RPO، RTO، زنجیره Full/Differential/Log، Encryption Key و دسترسی به رسانهٔ Backup را مستند کنید.
- Backup دیتابیس با Backup ماشین مجازی یا Veeam یکی نیست؛ هماهنگی Application-aware و سازگاری تراکنش‌ها را بررسی کنید.

### 19.8 SQL Agent و Jobها

```sql
SELECT name, enabled, date_created, date_modified
FROM msdb.dbo.sysjobs
ORDER BY name;

SELECT
  j.name AS job_name,
  msdb.dbo.agent_datetime(h.run_date,h.run_time) AS last_run,
  h.run_status, h.message
FROM msdb.dbo.sysjobs AS j
LEFT JOIN msdb.dbo.sysjobhistory AS h
  ON j.job_id=h.job_id AND h.step_id=0
WHERE h.instance_id IN (
  SELECT MAX(h2.instance_id)
  FROM msdb.dbo.sysjobhistory AS h2
  WHERE h2.job_id=j.job_id AND h2.step_id=0
)
ORDER BY last_run DESC;

SELECT name, enabled
FROM msdb.dbo.sysjobs
ORDER BY name;
```

در SSMS مسیر SQL Server Agent > Jobs > View History را هم بررسی کنید. شکست Job را با زمان اجرا، Owner، Credential/Proxy، دسترسی مسیر فایل، فضای دیسک و خروجی Step تحلیل کنید. Job یا Schedule را بدون تأیید مالک سرویس تغییر ندهید.

### 19.9 TempDB و کارایی

```sql
SELECT name, type_desc, physical_name, size*8.0/1024 AS size_mb
FROM tempdb.sys.database_files;

SELECT
  (SUM(unallocated_extent_page_count)*8.0)/1024 AS unallocated_mb,
  (SUM(version_store_reserved_page_count)*8.0)/1024 AS version_store_mb,
  (SUM(user_object_reserved_page_count)*8.0)/1024 AS user_objects_mb,
  (SUM(internal_object_reserved_page_count)*8.0)/1024 AS internal_objects_mb
FROM tempdb.sys.dm_db_file_space_usage;

SELECT TOP (20)
  wait_type, waiting_tasks_count, wait_time_ms, signal_wait_time_ms
FROM sys.dm_os_wait_stats
WHERE wait_type NOT LIKE 'SLEEP%'
ORDER BY wait_time_ms DESC;
```

برای تحلیل کارایی، CPU، Memory، Disk latency، Query duration، Waits، Execution Plan و تغییرات حجم داده را با baseline مقایسه کنید. پاک‌کردن Plan Cache یا اجرای Shrink به‌عنوان «بهینه‌سازی عمومی» توصیه نمی‌شود.

### 19.10 دسترسی و امنیت SQL

```sql
SELECT name, type_desc, is_disabled, create_date, modify_date
FROM sys.server_principals
WHERE type IN ('S','U','G')
ORDER BY name;

SELECT name, state_desc, is_read_only, containment_desc
FROM sys.databases;

SELECT
  dp.name AS database_user,
  dp.type_desc,
  rp.name AS database_role
FROM sys.database_role_members AS drm
JOIN sys.database_principals AS rp ON drm.role_principal_id=rp.principal_id
JOIN sys.database_principals AS dp ON drm.member_principal_id=dp.principal_id
ORDER BY database_role, database_user;
```

- برای سرویس‌ها از حساب سرویس اختصاصی با حداقل مجوز استفاده کنید؛ از اجرای SQL Server با Local Administrator یا حساب شخصی پرهیز کنید.
- مجوزهای `sysadmin`، `db_owner` و دسترسی مستقیم به دیتابیس مالی را محدود و دوره‌ای بازبینی کنید.
- رمز عبور، Connection String، Backup رمزگذاری‌شده و کلیدهای رمزگشایی را در گزارش یا مخزن کد قرار ندهید.
- برای نرم‌افزارهای مالی، ساختار و دادهٔ دیتابیس را مستقیم تغییر ندهید؛ عملیات اصلاحی فقط از مسیر رسمی سازنده و پس از Backup معتبر انجام شود.

### 19.11 Always On، Cluster و High Availability

در محیط‌هایی که Always On Availability Groups یا Failover Cluster Instance دارند، ابتدا نوع معماری و نسخه را شناسایی کنید. دستورات زیر صرفاً مشاهده‌ای‌اند و برای همهٔ Editionها/نسخه‌ها در دسترس نیستند:

```sql
SELECT
  ag.name AS availability_group,
  ar.replica_server_name,
  ars.role_desc,
  ars.operational_state_desc,
  ars.connected_state_desc,
  ars.synchronization_health_desc
FROM sys.availability_groups AS ag
JOIN sys.availability_replicas AS ar
  ON ag.group_id=ar.group_id
LEFT JOIN sys.dm_hadr_availability_replica_states AS ars
  ON ar.replica_id=ars.replica_id;

SELECT
  DB_NAME(database_id) AS database_name,
  synchronization_state_desc,
  synchronization_health_desc,
  is_suspended,
  suspend_reason_desc
FROM sys.dm_hadr_database_replica_states;
```

Failover، Resume/Join کردن Replica، تغییر Quorum و جابه‌جایی Role عملیات حساس‌اند؛ فقط با Runbook، تأیید مسئول سرویس و بررسی سلامت Replicaها انجام شوند.

## 20) تست پورت‌های متداول زیرساخت

| سرویس | پورت | کاربرد |
|---|---:|---|
| DNS | TCP/UDP 53 | Name resolution |
| DHCP | UDP 67/68 | دریافت IP |
| Kerberos | TCP/UDP 88 | احراز هویت AD |
| NTP | UDP 123 | همگام‌سازی ساعت |
| LDAP / LDAPS | TCP 389 / 636 | دایرکتوری |
| SMB | TCP 445 | فایل‌سرور |
| RPC Endpoint Mapper | TCP 135 | برخی عملیات Windows/AD |
| Global Catalog | TCP 3268 / 3269 | جست‌وجوی AD |
| RDP | TCP/UDP 3389 | دسترسی راه دور |
| HTTP / HTTPS | TCP 80 / 443 | وب |
| SQL Server | TCP 1433* | SQL پیش‌فرض |
| SQL Browser | UDP 1434* | کشف Instance نام‌دار |
| SIP | UDP/TCP 5060، TLS 5061* | سیگنالینگ VoIP |
| ESXi Host Client | TCP 443 | مدیریت HTTPS |
| SSH | TCP 22 | مدیریت امن |

`*` پورت واقعی باید از تنظیمات سرویس خوانده شود؛ از فرض‌کردن پورت پیش‌فرض خودداری کنید.

## 21) سناریوهای عملی آزمایش و عیب‌یابی

### سناریو A — کاربر IP می‌گیرد اما اینترنت ندارد
1. `ipconfig /all` یا `ip -br a`
2. Ping به Gateway
3. بررسی Route
4. Ping به IP عمومی
5. `nslookup` یک دامنه
6. بررسی Firewall/NAT و DNS Forwarder

### سناریو B — فقط یک VLAN قطع است
1. وضعیت پورت Access و VLAN عضویت
2. Trunk allowed VLAN و Native VLAN
3. SVI/Gateway و Route
4. DHCP Scope و Relay
5. ACL/Firewall Policy
6. تست از یک کلاینت سالم در همان VLAN

### سناریو C — ورود Domain انجام نمی‌شود
1. ساعت و Time Zone
2. DNS کلاینت و SRV Record
3. `nltest /dsgetdc:<domain>`
4. `dcdiag /test:dns`
5. Replication با `repadmin /replsummary`
6. Event Logهای System، DNS Server و GroupPolicy

### سناریو D — پوشهٔ اشتراکی باز نمی‌شود
1. Resolve نام File Server
2. تست TCP 445
3. بررسی SMB Service/Share
4. بررسی Share Permission و NTFS ACL
5. `whoami /all` و Credentialهای ذخیره‌شده
6. تست با حساب مجاز دیگر و ثبت خطای دقیق

### سناریو E — تماس VoIP وصل می‌شود اما صدا یک‌طرفه است
1. وضعیت Registration و Contact
2. NAT و External/Local Address
3. RTP port range و Firewall
4. مسیر رفت و برگشت RTP با Capture
5. Codec negotiation و Direct Media
6. تست داخلی به داخلی و سپس شهری

### سناریو F — Backup موفق گزارش می‌شود اما Restore نامطمئن است
1. بررسی آخرین Restore Point
2. سلامت Repository و فضای آزاد
3. بررسی Backup Copy و Offsite/Immutable copy
4. اجرای SureBackup یا Restore Test در صورت وجود مجوز و زیرساخت
5. ثبت RTO/RPO واقعی و مستندکردن نتیجهٔ بازیابی

### سناریو G — نرم‌افزار حسابداری به SQL متصل نمی‌شود
1. نام سرور و Instance را از تنظیمات رسمی برنامه ثبت کنید.
2. DNS سرور را با `Resolve-DnsName` بررسی کنید.
3. پورت واقعی SQL را از Configuration Manager/DBA دریافت کنید و با `Test-NetConnection` تست کنید.
4. سرویس Database Engine و SQL Error Log را بررسی کنید.
5. Login، مجوز Database، نسخهٔ کلاینت و Driver را بررسی کنید.
6. از تغییر مستقیم جداول مالی خودداری کنید؛ پس از Backup معتبر و تأیید مسئول مالی، فقط مسیر پشتیبانی رسمی را دنبال کنید.

## 22) الگوی ثبت هر تمرین در مسیر رشد

برای هر آزمایش این اطلاعات ثبت شود:
- هدف و نشانهٔ خرابی
- نقشهٔ سادهٔ توپولوژی و IP/VLAN
- نسخه و مدل تجهیزات/نرم‌افزار
- فرمان اجراشده و زمان اجرا
- خروجی خام (با حذف رمز، Token، IP عمومی حساس و اطلاعات کاربران)
- تفسیر خروجی و فرضیهٔ علت
- اقدام اصلاحی و ریسک آن
- تست قبل/بعد و نتیجه
- روش Rollback
- لینک مستند رسمی Vendor

## قواعد ایمنی

- فرمان‌های `erase`، `format`، `reset`، `delete`، `clear`، تغییر Firewall/ACL، تغییر VLAN، Restart سرویس و تغییر RAID را بدون مجوز، Backup و برنامهٔ بازگشت اجرا نکنید.
- رمز عبور، Community String، Private Key، فایل پیکربندی کامل و Capture تماس را در گزارش عمومی قرار ندهید.
- خروجی‌های دستوری ممکن است با نسخهٔ سیستم‌عامل یا Firmware تفاوت داشته باشند؛ قبل از اجرا `help`، `?` یا مستند همان نسخه را بررسی کنید.


## 23) رنج پورت‌ها و مرجع TCP/UDP

| محدوده | نام IANA | توضیح |
|---|---|---|
| 0–1023 | System / Well-Known | سرویس‌های شناخته‌شده |
| 1024–49151 | User / Registered | سرویس‌های ثبت‌شده |
| 49152–65535 | Dynamic / Private | محدوده پویا/خصوصی IANA؛ معمولاً پورت موقت کلاینت |

محدوده Ephemeral سیستم‌عامل ممکن است متفاوت یا قابل تنظیم باشد؛ آن را با Dynamic/Private در IANA یکی نگیرید. پورت به‌تنهایی سرویس را اثبات نمی‌کند؛ TCP/UDP، Listener واقعی، نسخه و جهت ارتباط را بررسی کنید.

| پورت | پروتکل | سرویس / توضیح |
|---:|---|---|
| 20/21 | TCP | FTP data/control؛ در صورت امکان SFTP/FTPS |
| 22 | TCP | SSH / SFTP |
| 23 | TCP | Telnet؛ ناامن و معمولاً غیرفعال |
| 25 | TCP | SMTP relay |
| 53 | TCP/UDP | DNS |
| 67/68 | UDP | DHCP Server/Client |
| 69 | UDP | TFTP؛ فاقد رمزنگاری |
| 80/443 | TCP | HTTP/HTTPS |
| 88 | TCP/UDP | Kerberos |
| 110/995 | TCP | POP3 / POP3S |
| 123 | UDP | NTP |
| 135 | TCP | RPC Endpoint Mapper در Windows |
| 137–139 | TCP/UDP | NetBIOS؛ فقط در صورت نیاز Legacy |
| 143/993 | TCP | IMAP / IMAPS |
| 161/162 | UDP | SNMP Query/Trap؛ ترجیحاً SNMPv3 |
| 389/636 | TCP/UDP و TCP | LDAP / LDAPS |
| 445 | TCP | SMB |
| 465/587 | TCP | SMTP Submission |
| 514 | UDP/TCP | Syslog؛ وابسته به پیاده‌سازی |
| 546/547 | UDP | DHCPv6 |
| 1812/1813 | UDP | RADIUS |
| 2049 | TCP/UDP | NFS |
| 3268/3269 | TCP | Active Directory Global Catalog |
| 3306 | TCP | MySQL |
| 3389 | TCP/UDP | RDP؛ فقط از VPN/شبکه مدیریت |
| 5432 | TCP | PostgreSQL |
| 5060/5061 | UDP/TCP و TCP | SIP / SIP over TLS |
| 5985/5986 | TCP | WinRM HTTP/HTTPS |
| 1433 | TCP | SQL Server پیش‌فرض؛ Instance ممکن است متفاوت باشد |
| 1434 | UDP | SQL Server Browser؛ فقط در صورت نیاز |
| 4172 | TCP/UDP | بعضی پیاده‌سازی‌های PCoIP |
| 8080/8443 | TCP | وب جایگزین؛ وابسته به برنامه |
| 10000–20000 | UDP | نمونه رایج RTP در بعضی PBXها؛ رنج واقعی را از Asterisk بخوانید |

فرمان‌های مشاهده:
    PowerShell: Get-NetTCPConnection; Get-NetUDPEndpoint; Get-NetTCPConnection -State Listen
    Linux: ss -lntup; sudo lsof -i -P -n; nc -vz <host> <port>
    Test TCP: Test-NetConnection <host> -Port 445
اسکن فقط روی دارایی تحت مالکیت یا دارای مجوز کتبی انجام شود. برای هر Rule مبدأ، مقصد، پروتکل، پورت، جهت و مالک سرویس را ثبت کنید.

## 24) امنیت MikroTik و سیاست Firewall

فرمان‌های مشاهده:
    /system resource print
    /system package print
    /user print detail
    /ip service print
    /ip ssh print
    /ip firewall filter print stats detail
    /ip firewall nat print stats detail
    /ip firewall address-list print
    /ip firewall connection print count-only
    /log print

چک‌لیست Hardening:
- سرویس‌های مدیریتی غیرضروری را غیرفعال و Winbox/SSH/API را فقط به Management VLAN یا VPN محدود کنید.
- حساب مدیریتی شخصی و رمز قوی/منحصربه‌فرد داشته باشید.
- زنجیره‌های Input، Forward و Output را جداگانه طراحی کنید؛ Established/Related را درست مدیریت و Drop نهایی مستند داشته باشید.
- NAT کنترل دسترسی نیست؛ Port Forward فقط برای مقصد و مبدأ ضروری و با محدودیت مشخص.
- پیش از تغییر حساس Backup، Export پاک‌سازی‌شده، Safe Mode و دسترسی کنسول جایگزین آماده کنید.
- IPv6 را نیز جداگانه ممیزی کنید؛ Firewall IPv4 جایگزین سیاست IPv6 نیست.

## 25) امنیت Issabel / Asterisk و VoIP

فرمان‌های مشاهده:
    asterisk -rx 'core show version'
    asterisk -rx 'pjsip show endpoints'
    asterisk -rx 'pjsip show contacts'
    asterisk -rx 'pjsip show registrations'
    asterisk -rx 'core show channels'
    ss -lntup
    journalctl -u asterisk --since '1 hour ago'

اصول امنیتی:
- پنل وب و SSH را از اینترنت عمومی در دسترس نگذارید؛ مدیریت از VPN یا شبکه مدیریت انجام شود.
- SIP را به IPهای Trunk و نقاط مورد اعتماد محدود کنید؛ داخلی‌ها رمز یکتا و قوی داشته باشند.
- تماس بین‌الملل و مقصدهای پرهزینه را محدود و سقف هزینه/تماس و هشدار مصرف تعریف کنید.
- Fail2ban یا کنترل معادل را با Allowlist اپراتور هماهنگ کنید تا Trunk قانونی مسدود نشود.
- SIP Signaling و RTP Media متفاوت‌اند؛ رنج RTP واقعی را در نسخه و تنظیم Asterisk بررسی کنید.
- CDR، Security Event، SIP Registration و تماس‌های ناموفق را پایش کنید؛ PBX و Firewall باید NTP هماهنگ داشته باشند.
- Backup تنظیمات، CDR، Voicemail و فایل‌های لازم را تهیه و Restore را در محیط ایزوله آزمایش کنید.

منبع فارسی تکمیلی: [دوره رایگان آموزش ایزابل در PBXShop](https://pbxshop.co/free-issabel/). این صفحه به‌عنوان منبع مطالعه معرفی می‌شود؛ متن و تصاویر متعلق به ناشر هستند و بازنشر داخل برنامه نیازمند بررسی مجوز/اجازه جداگانه است.

## 26) آنتن، لینک وایرلس و عیب‌یابی RF

در هر لینک Point-to-Point/Point-to-Multipoint ثبت کنید: مدل و Firmware دو رادیو، فرکانس، Channel Width، توان، نوع آنتن، Line of Sight، Fresnel Zone، RSSI، Noise Floor، SNR، CCQ/airtime، MCS/PHY rate، Retry، Packet loss و Throughput واقعی. موانع فصلی، نویز هم‌کانال، DFS، کابل، کانکتور، ارت و PoE را هم بررسی کنید. محدودیت قانونی فرکانس و توان در محل نصب رعایت شود.

فرمان‌های Linux:
    iw dev
    iw dev wlan0 link
    iw dev wlan0 station dump
    iw dev wlan0 survey dump

فرمان‌های MikroTik (وابسته به نسل RouterOS و بسته WiFi/Wireless):
    /interface wifi print detail
    /interface wifi registration-table print stats
    /interface monitor-traffic <interface>
    /tool torch <interface>

قدرت سیگنال به‌تنهایی کیفیت لینک را نشان نمی‌دهد؛ SNR، تداخل، Retry و ظرفیت واقعی را با هم تحلیل کنید.

## 27) Python و Git برای اتوماسیون و تغییرات

Python:
    python --version
    python -m venv .venv
    Linux/macOS: source .venv/bin/activate
    Windows PowerShell: .\.venv\Scripts\Activate.ps1
    python -m pip install --upgrade pip
    python -m pip freeze > requirements.txt
    python -m compileall .

در اسکریپت عملیاتی Timeout، Exception handling، Logging و حالت Dry-run داشته باشید. رمز و Token را در کد یا Git قرار ندهید؛ وابستگی‌ها را در venv و requirements ثبت کنید.

Git:
    git status --short
    git switch -c feature/short-description
    git diff --check
    git diff
    git add <file>
    git commit -m "ops: document diagnostic workflow"
    git log --oneline --decorate -10
    git fetch origin
    git diff origin/main...HEAD
    git restore --staged <file>

از reset --hard، clean -xfd و push --force به‌عنوان راه‌حل روزمره استفاده نکنید؛ ممکن است کار ثبت‌نشده یا تاریخچه مشترک را از بین ببرند.

## 28) Windows Services، پردازش‌ها و فایل‌های باز

فرمان‌های مشاهده:
    Get-Service
    Get-CimInstance Win32_Service | Select-Object Name,State,StartMode,ProcessId
    Get-Process | Sort-Object CPU -Descending | Select-Object -First 20
    Get-NetTCPConnection -State Listen
    Get-Process -Id <PID>
    Get-WinEvent -LogName System -MaxEvents 100

توقف کنترل‌شده:
    Stop-Service -Name <ServiceName> -WhatIf
    Stop-Service -Name <ServiceName>
    Restart-Service -Name <ServiceName>
    Stop-Process -Id <PID> -WhatIf
    Stop-Process -Id <PID>
    CMD: tasklist /svc
    CMD: taskkill /PID <PID> /T

ابتدا WhatIf، وابستگی سرویس، مالک پردازش و اثر روی کاربران را بررسی کنید. برای فایل باز روی File Server:
    Get-SmbOpenFile
    Get-SmbSession
    Close-SmbOpenFile -FileId <FileId> -WhatIf
    Close-SmbOpenFile -FileId <FileId>

فقط پس از شناسایی Share، مسیر، شناسه و کاربر و هماهنگی با مالک فایل را ببندید؛ بستن فایل ممکن است ذخیره داده کاربر را مختل کند.

Linux:
    ps -fp <PID>
    lsof -p <PID>
    lsof /path/to/file
    fuser -v /path/to/file
    kill -TERM <PID>
    kill -KILL <PID>   # آخرین راه، پس از بررسی مجدد

ابتدا SIGTERM و فرصت خاتمه کنترل‌شده؛ SIGKILL آخرین راه است.

## 29) تحلیل لاگ و پاسخ به رخداد

1. ساعت و Timezone سیستم را با NTP بررسی کنید.
2. زمان شروع، آخرین زمان سالم، کاربر، میزبان، IP مبدأ/مقصد و سرویس را ثبت کنید.
3. لاگ Endpoint، Windows/Linux، DNS/DHCP، Switch، Firewall، PBX و برنامه را در یک Timeline قرار دهید.
4. Login ناموفق، تغییر پیکربندی، قطع Interface، Restart سرویس و تغییر حجم ترافیک را بررسی کنید.
5. شواهد خام را با زمان و Hash نگهداری کنید؛ قبل از جمع‌آوری، لاگ را پاک یا سیستم را بی‌دلیل Restart نکنید.
6. مهار را طبق Runbook انجام دهید؛ دامنه اثر، ریشه مشکل و اصلاح را گزارش کنید.

PowerShell:
    Get-WinEvent -LogName Security -MaxEvents 200
    Get-WinEvent -LogName System -MaxEvents 200
    Get-WinEvent -FilterHashtable @{LogName='System'; Level=2; StartTime=(Get-Date).AddHours(-2)}

Linux:
    journalctl --since '2 hours ago'
    journalctl -p warning..alert
    journalctl -u <service> --since today
    grep -iE 'error|failed|denied|timeout' /var/log/<logfile>

## 30) تست نفوذ مجاز و ارزیابی امنیت

تست نفوذ فقط با مجوز کتبی مالک دارایی و Scope دقیق انجام می‌شود. Scope باید IP/دامنه، زمان، ابزار و نرخ درخواست، تماس اضطراری، داده‌های ممنوع، محدودیت DoS، Backup و معیار توقف را مشخص کند.

فقط در آزمایشگاه یا دارایی صریحاً مجاز:
    nmap -sT -sV --top-ports 100 <LAB-IP>
    nmap -sT -p 22,80,443,445 <LAB-IP>
    ss -lntup
    sudo lynis audit system

یافته را با دارایی، سرویس، نسخه، شواهد، شدت، احتمال، اثر کسب‌وکار و راهکار اصلاح ثبت کنید. از اجرای Exploit، Brute-force، Password spraying یا اسکن خارج از Scope خودداری کنید. برای وب، OWASP Top 10 و OWASP ZAP را ابتدا در حالت Passive/Baseline و سپس فقط با مجوز در آزمایشگاه بررسی کنید.

## 31) ماتریس سریع جریان‌های Firewall

| فیلد | نمونه |
|---|---|
| مبدأ | VLAN کاربران 192.0.2.0/24 |
| مقصد | DNS Server 192.0.2.53 |
| پروتکل/پورت | UDP/TCP 53 |
| جهت | Client → DNS |
| مالک سرویس | تیم زیرساخت |
| دلیل کسب‌وکار | Name resolution |
| ثبت لاگ | فقط در سطح لازم |
| تاریخ بازبینی | تاریخ Change/Review |
| نتیجه تست | نام، زمان، ابزار و خروجی |

قواعد بین VLAN دوربین، سرور، مالی، کاربران، VoIP و مدیریت باید جدا باشند. از Any-to-any پرهیز کنید و دسترسی مدیریت تجهیزات را از VLAN کاربران جدا نگه دارید.


## 32) منابع آموزشی تکمیلی PBXShop

این منابع برای مطالعهٔ تکمیلی Issabel، VoIP، MikroTik و امنیت شبکه معرفی می‌شوند. صفحات ناشر شامل آموزش‌های مرحله‌ای و در چند بخش تصاویر محیط واقعی و ویدئو هستند؛ برای مشاهدهٔ تصویرها و نسخهٔ به‌روز محتوا، لینک اصلی را باز کنید. متن، ویدئو و تصاویر ناشر در برنامه بازنشر نشده‌اند و صرفاً به‌صورت پیوند خارجی معرفی شده‌اند.

### Issabel و Asterisk
- [دوره رایگان آموزش ایزابل](https://pbxshop.co/free-issabel/) — فهرست جلسات و سناریوهای عملی.
- [آشنایی با محیط وب و داشبورد Issabel](https://pbxshop.co/issabel-dashboard/) — System، Network، PBX، Reports، Security و نمایش منابع و وضعیت ماژول‌ها؛ همراه تصاویر محیط.
- [ساخت و راه‌اندازی داخلی‌ها در Issabel](https://pbxshop.co/ext-issabel/) — Extension، Softphone، IP Phone، Secret و ثبت داخلی؛ همراه مراحل تصویری.
- [نصب Issabel روی سرور فیزیکی](https://pbxshop.co/issabel-local/) — راهنمای نصب مرحله‌ای روی سخت‌افزار.
- [راه‌اندازی SIP Trunk و تجهیزات موردنیاز](https://pbxshop.co/setting-up-sip-trunk/) — پیش‌نیازهای ارتباط ترانک و مسیر تماس.

### امنیت VoIP و MikroTik
- [دوره رایگان امنیت VoIP و شبکه](https://pbxshop.co/voip-network-security/) — مباحث Filter Rules، Address Lists، پورت‌های مدیریتی، ICMP، پکت‌های نامعتبر و اصول کاهش سطح حمله.
- [دوره رایگان MikroTik](https://pbxshop.co/free-courses/free-mikrotik-course/) — مکمل درس‌های RouterOS، Firewall و مدیریت شبکه.

### روش استفاده در مسیر رشد
1. ابتدا درس متناظر MindUnlock را کامل کنید و دستورهای تشخیصی را در آزمایشگاه اجرا کنید.
2. سپس مقاله یا ویدئوی مرتبط PBXShop را برای مشاهدهٔ مراحل تصویری باز کنید.
3. نسخه و محیط نرم‌افزار را کنترل کنید؛ منوها و دستورات نسخه‌های قدیمی را بدون تطبیق روی سامانهٔ عملیاتی اجرا نکنید.
4. تغییرات Firewall، SIP Trunk، NAT و دسترسی مدیریتی را فقط با Backup، پنجرهٔ نگهداری و مسیر بازگشت اجرا کنید.
