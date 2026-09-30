FROM amazoncorretto:21

WORKDIR /app

COPY target/httpserver-1.0-SNAPSHOT.jar app.jar

ENV PORT=9000

EXPOSE 9000

ENTRYPOINT ["java", "-jar", "app.jar"]