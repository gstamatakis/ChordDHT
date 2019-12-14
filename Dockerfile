# fetch basic image
FROM maven:3.5.0-jdk-8-alpine
MAINTAINER gstamatakis

RUN apk update
RUN apk add git
RUN apk add tree

ENV GIT_SSL_NO_VERIFY true

RUN mkdir /app

WORKDIR /app

RUN git clone https://github.com/gstamatakis/ChordDHT.git /app && cd /app

RUN  mvn clean compile assembly:single

EXPOSE 1099 21 22

RUN tree