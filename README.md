### Github Actions를 통한 배포 자동화

> [Github Actions](https://docs.github.com/en/actions)
- Github이 공식적으로 제공하는 빌드, 테스트 및 배포 파이프라인을 자동화할 수 있는 CI/CD 플랫폼
- 레포지토리에서 Pull Request 나 push 같은 이벤트를 트리거로 GitHub 작업 워크플로(Workflow)를 구성할 수 있다.

[![](https://velog.velcdn.com/images/wish17/post/7b4924ee-413e-479a-a5bd-167854a89f09/image.png)](https://github.com/jojoldu/freelec-springboot2-webservice/issues/806)

설정 파일(``.yml``)에 따라 Github Repository의 특정 변동사항을 트리거로 작동된다.
(실습에서는 main 브랜치에 push 하는 경우 작동되도록 했다.)

#### 1. GitHub Actions 생성

![](https://velog.velcdn.com/images/wish17/post/1c456259-b800-4c4f-855d-e776c6252a6c/image.png)

![](https://velog.velcdn.com/images/wish17/post/226668f5-8aca-4121-a4c8-762009f670ed/image.png)

![](https://velog.velcdn.com/images/wish17/post/1289ea2f-29b0-420f-abf7-92107ebc8bdc/image.png)


위와 같이 ``gradle.yml``파일 자동 생성하니 ``'~/gradlew' is not executable`` 오류 발생

아래와 같이 Build with Gradle 이전에 ``./gradlew``에 권한을 부여해서 해결했다.

```java
name: Java CI with Gradle

on:
  push:
    branches: [ "master" ]
  pull_request:
    branches: [ "master" ]

permissions:
  contents: read

env:
  S3_BUCKET_NAME: be-58-wish9

jobs:
  build:

    runs-on: ubuntu-latest

    steps:
    - uses: actions/checkout@v3
    - name: Set up JDK 11
      uses: actions/setup-java@v3
      with:
        java-version: '11'
        distribution: 'temurin'
    - name: Add permission # '~/gradlew' is not executable. 에러 때문에 추가
      run: chmod +x gradlew # '~/gradlew' is not executable. 에러 때문에 추가
    - name: Build with Gradle
      uses: gradle/gradle-build-action@67421db6bd0bf253fb4bd25b31ebb98943c375e1
      with:
        arguments: build
```

aws access 설정 추가 + 빌드파일 S3에 전달

- Github Actions는 설정 파일(.yml)에 따라 Github Repository에 특정 변동사항을 트리거로 작동한다.

```java
name: Java CI with Gradle

on:
  push:
    branches: [ "master" ]
  pull_request: # 없어도 됨 (누군가가 해당 레파지토리에서 pull하면 자동배포 되는거임) 
    branches: [ "master" ]

permissions:
  contents: read

env:
  S3_BUCKET_NAME: be-58-wish9

jobs:
  build:

    runs-on: ubuntu-latest

    steps:
    - uses: actions/checkout@v3
    - name: Set up JDK 11
      uses: actions/setup-java@v3
      with:
        java-version: '11'
        distribution: 'temurin'
    - name: Add permission # '~/gradlew' is not executable. 에러 때문에 추가
      run: chmod +x gradlew # '~/gradlew' is not executable. 에러 때문에 추가
    - name: Build with Gradle
      uses: gradle/gradle-build-action@67421db6bd0bf253fb4bd25b31ebb98943c375e1
      with:
        arguments: build

      # build한 후 프로젝트를 압축
    - name: Make zip file
      run: zip -r ./practice-deploy.zip .
      shell: bash

      # Access Key와 Secret Access Key를 통해 권한을 확인
      # Access Key와 Secret Key는 직접 작성 X (깃 설정에서 추가)
    - name: Configure AWS credentials
      uses: aws-actions/configure-aws-credentials@v1
      with:
        aws-access-key-id: ${{ secrets.AWS_ACCESS_KEY }} # 등록한 Github Secret이 자동으로 불려와진다.
        aws-secret-access-key: ${{ secrets.AWS_SECRET_ACCESS_KEY }} # 등록한 Github Secret이 자동으로 불려와진다.
        aws-region: ap-northeast-2

      # 압축한 프로젝트를 S3로 전송
    - name: Upload to S3
      run: aws s3 cp --region ap-northeast-2 ./practice-deploy.zip s3://$S3_BUCKET_NAME/practice-deploy.zip

```

프로젝트 설정에서 access key 등록

![](https://velog.velcdn.com/images/wish17/post/df34f734-8513-4116-890a-fb55b0ea7ed5/image.png)

인텔리제이에서 수정했던 ``gradle.yml``파일 push
(자동생성 된 ``gradle.yml``파일을 pull해서 수정했음)

![](https://velog.velcdn.com/images/wish17/post/89098d00-602f-4c9b-a9b9-eae48b2620c2/image.png)

자동배포 정상작동!

![](https://velog.velcdn.com/images/wish17/post/7cd7ad32-39ca-4020-9f40-d79901b89866/image.png)

S3 버킷에 압축파일이 전송된 것도 확인

#### 2. 빌드파일 배포 및 실행

애플리케이션 + 그룹 생성

IAM 권한에 ``AWSCodeDeployRole`` 추가되어 있어야 생성 가능

![](https://velog.velcdn.com/images/wish17/post/6a5fabc4-ea29-484f-95b5-b8f4a6af8515/image.png)

> AWSCodeDeployRole
- AWS CodeDeploy 애플리케이션 및 배포 그룹 생성 및 수정 권한
- AWS CodeDeploy 배포 작업을 위한 EC2 인스턴스 및 온프레미스 서버에 대한 액세스 권한
- AWS CodeDeploy 배포 작업에서 Amazon S3 버킷에 대한 읽기 권한
- AWS CodeDeploy 배포 작업에서 Amazon EC2 Systems Manager에 대한 권한


![](https://velog.velcdn.com/images/wish17/post/8dd5ce61-91ec-4917-ac71-7e29363ca074/image.png)

EC2 배포 진행 상황 별 로그를 기록하고 새로 배포된 빌드 파일을 실행하는 shell script 파일(``deploy.sh``) 생성

```java
#!/bin/bash
BUILD_JAR=$(ls /home/ubuntu/action/build/libs/practice-githubAction-0.0.1-SNAPSHOT.jar)
JAR_NAME=$(basename $BUILD_JAR)

echo "> 현재 시간: $(date)" >> /home/ubuntu/action/deploy.log

echo "> build 파일명: $JAR_NAME" >> /home/ubuntu/action/deploy.log

echo "> build 파일 복사" >> /home/ubuntu/action/deploy.log
DEPLOY_PATH=/home/ubuntu/action/
cp $BUILD_JAR $DEPLOY_PATH

echo "> 현재 실행중인 애플리케이션 pid 확인" >> /home/ubuntu/action/deploy.log
CURRENT_PID=$(pgrep -f $JAR_NAME)

if [ -z $CURRENT_PID ]
then
  echo "> 현재 구동중인 애플리케이션이 없습니다." >> /home/ubuntu/action/deploy.log
else
  echo "> kill -9 $CURRENT_PID" >> /home/ubuntu/action/deploy.log
  sudo kill -9 $CURRENT_PID
  sleep 5
fi


DEPLOY_JAR=$DEPLOY_PATH$JAR_NAME
echo "> DEPLOY_JAR 배포"    >> /home/ubuntu/action/deploy.log
sudo nohup java -jar $DEPLOY_JAR >> /home/ubuntu/deploy.log 2>/home/ubuntu/action/deploy_err.log &
```


Code Deploy의 작동을 모아놓은 ``appspec.yml`` 파일 설정

```java
version: 0.0
os: linux
files:
  - source:  /
    destination: /home/ubuntu/action # 배포 진행되는 디렉토리 주소
    overwrite: yes

permissions:
  - object: /
    pattern: "**"
    owner: ubuntu
    group: ubuntu

hooks:
  ApplicationStart:
    - location: scripts/deploy.sh # 최상위 디렉토리에서 해당 경로에 있는 deploy라는 이름의 쉘 스크립트 실행
      timeout: 60
      runas: ubuntu
```

``gradle.yml`` 파일에 Code Deploy 배포 명령 추가

```java
name: Java CI with Gradle

on:
  push:
    branches: [ "master" ]
  pull_request:
    branches: [ "master" ]

permissions:
  contents: read

env:
  S3_BUCKET_NAME: be-58-wish9

jobs:
  build:

    runs-on: ubuntu-latest

    steps:
    - uses: actions/checkout@v3
    - name: Set up JDK 11
      uses: actions/setup-java@v3
      with:
        java-version: '11'
        distribution: 'temurin'
    - name: Add permission # '~/gradlew' is not executable. 에러 때문에 추가
      run: chmod +x gradlew # '~/gradlew' is not executable. 에러 때문에 추가
    - name: Build with Gradle
      uses: gradle/gradle-build-action@67421db6bd0bf253fb4bd25b31ebb98943c375e1
      with:
        arguments: build

      # build한 후 프로젝트를 압축
    - name: Make zip file
      run: zip -r ./practice-deploy.zip .
      shell: bash

      # Access Key와 Secret Access Key를 통해 권한을 확인
      # Access Key와 Secret Key는 직접 작성 X (깃 설정에서 추가)
    - name: Configure AWS credentials
      uses: aws-actions/configure-aws-credentials@v1
      with:
        aws-access-key-id: ${{ secrets.AWS_ACCESS_KEY }} # 등록한 Github Secret이 자동으로 불려와진다.
        aws-secret-access-key: ${{ secrets.AWS_SECRET_ACCESS_KEY }} # 등록한 Github Secret이 자동으로 불려와진다.
        aws-region: ap-northeast-2

      # 압축한 프로젝트를 S3로 전송
    - name: Upload to S3
      run: aws s3 cp --region ap-northeast-2 ./practice-deploy.zip s3://$S3_BUCKET_NAME/practice-deploy.zip

-------------------------------------추가된 부분-------------------------------------
      # CodeDeploy에게 배포 명령을 내립니다.
    - name: Code Deploy
      run: >
        aws deploy create-deployment --application-name be-58-wish9
        --deployment-config-name CodeDeployDefault.AllAtOnce
        --deployment-group-name be-58-wish9-group
        --s3-location bucket=$S3_BUCKET_NAME,bundleType=zip,key=practice-deploy.zip
-------------------------------------추가된 부분-------------------------------------
```

수정, 추가한 파일 깃허브로 push하면 아래와 같이 배포 성공 확인가능

![](https://velog.velcdn.com/images/wish17/post/2d133885-ca51-4a31-af47-9f2e5ff15429/image.png)

![](https://velog.velcdn.com/images/wish17/post/292a1c67-ac83-4b86-adbc-953e9356ccbc/image.png)



자동 배포 설정해둔 경로

```bash
ssm-user@ip-172-31-34-158:/var/snap/amazon-ssm-agent/6312$ cd ~
ssm-user@ip-172-31-34-158:~$ cd /home
ssm-user@ip-172-31-34-158:/home$ ls
ssm-user  ubuntu
ssm-user@ip-172-31-34-158:/home$ cd ubuntu/
ssm-user@ip-172-31-34-158:/home/ubuntu$ ls
action  build  deploy.log  install
ssm-user@ip-172-31-34-158:/home/ubuntu$ cd action
ssm-user@ip-172-31-34-158:/home/ubuntu/action$ ls
appspec.yml  build  build.gradle  deploy.log  deploy_err.log  gradle  gradlew  gradlew.bat  practice-githubAction-0.0.1-SNAPSHOT.jar  scripts  settings.gradle  src
```

![](https://velog.velcdn.com/images/wish17/post/9688a0e7-6a82-43ed-b153-f5f8811323bc/image.png)

![](https://velog.velcdn.com/images/wish17/post/576f8e7b-5f3a-49e2-8d9b-dbd5f751c5c7/image.png)


#### Github Actions 다시 실행하는 방법


![](https://velog.velcdn.com/images/wish17/post/3d663f76-07f7-4375-9dc9-fbc34ed462ca/image.png)

***

### 응용실습

예전에 파이썬으로 만들었던 프로젝트를 활용해서 간단한 방명록 애플리케이션을 만들어서 배포해봤다.

[풀코드 GitHub 주소](https://github.com/wish9/practice-githubAction/commit/7339dfc82245ba321d6a850ccc04c6c593343897#diff-54eeffbae371fcd1398d4ca5e89a1b8118208b7bb2f8ddf55c1aa2f7d98ab136)


![](https://velog.velcdn.com/images/wish17/post/9e1679ed-050b-4807-994d-8f2570e88f2b/image.png)
