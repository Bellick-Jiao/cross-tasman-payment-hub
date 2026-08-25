mqsivault --work-dir "C:\Users\jiaoj\IBM\ACET13\workspace\TEST_SERVER" --create --vault-key MySecretKey123

mqsicredentials --work-dir "C:\Users\jiaoj\IBM\ACET13\workspace\TEST_SERVER" --create --credential-type mq --credential-name aklMqAuth --username app --password Password99 --vault-key MySecretKey123



# 1. 授予队列管理器级别的所有管理与连接权限
docker exec -i mq-akl-pr2 setmqaut -m QM_AKL_PR2 -t qmgr -p app +alladm +allmqi

# 2. 授予所有队列 (*) 的全部访问权限 (get, put, inq, set, passall 等)
docker exec -i mq-akl-pr2 setmqaut -m QM_AKL_PR2 -t q -n '**' -p app +all

# 3. 授予所有主题与通道的全部权限
docker exec -i mq-akl-pr2 setmqaut -m QM_AKL_PR2 -t topic -n '**' -p app +all
docker exec -i mq-akl-pr2 setmqaut -m QM_AKL_PR2 -t channel -n '**' -p app +all

# 4. 刷新安全认证缓存生效
docker exec -i mq-akl-pr2 runmqsc QM_AKL_PR2 <<EOF
REFRESH SECURITY TYPE(AUTHSERV)
EOF

# 对 mq-akl-pr1 授权
docker exec -i mq-akl-pr1 setmqaut -m QM_AKL_PR1 -t qmgr -p app +alladm +allmqi
docker exec -i mq-akl-pr1 setmqaut -m QM_AKL_PR1 -t q -n '**' -p app +all
docker exec -i mq-akl-pr1 setmqaut -m QM_AKL_PR1 -t topic -n '**' -p app +all
docker exec -i mq-akl-pr1 setmqaut -m QM_AKL_PR1 -t channel -n '**' -p app +all
docker exec -i mq-akl-pr1 runmqsc QM_AKL_PR1 <<< "REFRESH SECURITY TYPE(AUTHSERV)"

# 对 mq-akl-fr 授权
docker exec -i mq-akl-fr setmqaut -m QM_AKL_FR -t qmgr -p app +alladm +allmqi
docker exec -i mq-akl-fr setmqaut -m QM_AKL_FR -t q -n '**' -p app +all
docker exec -i mq-akl-fr setmqaut -m QM_AKL_FR -t topic -n '**' -p app +all
docker exec -i mq-akl-fr setmqaut -m QM_AKL_FR -t channel -n '**' -p app +all
docker exec -i mq-akl-fr runmqsc QM_AKL_FR <<< "REFRESH SECURITY TYPE(AUTHSERV)"





第二步：在 Windows 系统中添加系统级环境变量这是解决 IBM MQ C 客户端在 Windows 10/11 下读取 65001 的官方办法：在 Windows 搜索栏输入 “环境变量” $\rightarrow$ 打开 “编辑系统环境变量”。在 “系统变量 (System Variables)” 区域点击 新建：变量名：MQCCSID变量值：1208再次点击 新建：变量名：IBM_JAVA_OPTIONS变量值：-Dfile.encoding=UTF-8 -Dclient.encoding.override=1208点击确定保存。



2. 现在官方的最新拉取渠道在哪里？
IBM 已经将所有官方预构建的生产级镜像（包括最新的 ACE 13）统一发布在 IBM 官方云容器镜像注册表（IBM Cloud Container Registry，域名为 cp.icr.io）。
由于涉及授权许可，现在拉取镜像需要先获得一把免费的授权密钥。具体操作步骤如下：
获取专属授权密钥（IBM Entitlement Key）： 登录你的 IBM 账号，免费进入 IBM Container Library 官方页面 生成一把专属你账号的 Entitlement Key（授权密钥）。
在你的 Docker 环境执行安全登录： 在宿主机控制台输入以下命令（其中用户名固定为 cp，密码输入你刚刚复制的密钥）：
docker login cp.icr.io -u cp -p <你的Entitlement_Key>
直接拉取官方 ACE 13 镜像： 登录成功后，你就可以像往常一样非常愉快地执行 docker pull 了：
通用开发与本地 Docker Compose 部署镜像（推荐我们项目使用此类型）：
docker pull cp.icr.io/cp/appc/ace:13.0.8.1-r1
Certified Container 镜像（仅适用于 K8s / App Connect Operator 环境）：
docker pull cp.icr.io/cp/appc/ace-server-prod:13.0.8.0-r1


docker login cp.icr.io -u cp -p eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJJQk0gTWFya2V0cGxhY2UiLCJpYXQiOjE3ODc1MjkyNTQsImp0aSI6IjcyOTZkMTNhNTk1NjQxNGNiZDQ0ZjQ3NGQwMmZlZDM5In0.6tSCIEGk--b3ZTLr6HyYtvjCM3pccEfgjtL3e3VWyDQ

PS D:\workspace\antigravity-workspace\cross-tasman-payment-hub\auckland> docker login cp.icr.io -u cp -p eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJJQk0gTWFya2V0cGxhY2UiLCJpYXQiOjE3ODc1MjkyNTQsImp0aSI6IjcyOTZkMTNhNTk1NjQxNGNiZDQ0ZjQ3NGQwMmZlZDM5In0.6tSCIEGk--b3ZTLr6HyYtvjCM3pccEfgjtL3e3VWyDQ
WARNING! Using --password via the CLI is insecure. Use --password-stdin.
Login Succeeded


docker build -t ace --build-arg DOWNLOAD_URL=<download URL>  --file ./Dockerfile .

. /opt/ibm/ace-13/server/bin/mqsiprofile
mqsideploy --admin-port 7600 --bar-file /home/aceuser/initial-config/bars/ISO20022_PaymentGateway_Appproject.generated.bar --insecure
