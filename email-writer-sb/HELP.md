# Getting Started

### Translation API

Use the translation endpoint when you need to convert an incoming email to another language before crafting a reply.

* **Endpoint:** `POST /api/email/translate`
* **Request body example:**
  ```json
  {
    "emailContent": "Hi team, can we reschedule the call?",
    "sourceLanguage": "English",
    "targetLanguage": "Spanish"
  }
  ```
* **Response:** plain text containing the translated email (the service only returns the translation, not metadata).
* **Behavior:** The request requires `targetLanguage`. Omitting it returns HTTP 400. The translation is generated through the Gemini API, so you must still provide `gemini.api.url` and `gemini.api.key` in your environment.
* **Source language hints:** Provide `sourceLanguage` to explicitly translate from a known language (like Google Translate’s source/target pair). Leave it blank to let the model auto-detect the input language.

### Reference Documentation
For further reference, please consider the following sections:

* [Official Apache Maven documentation](https://maven.apache.org/guides/index.html)
* [Spring Boot Maven Plugin Reference Guide](https://docs.spring.io/spring-boot/4.0.1/maven-plugin)
* [Create an OCI image](https://docs.spring.io/spring-boot/4.0.1/maven-plugin/build-image.html)
* [Spring Web](https://docs.spring.io/spring-boot/4.0.1/reference/web/servlet.html)

### Guides
The following guides illustrate how to use some features concretely:

* [Building a RESTful Web Service](https://spring.io/guides/gs/rest-service/)
* [Serving Web Content with Spring MVC](https://spring.io/guides/gs/serving-web-content/)
* [Building REST services with Spring](https://spring.io/guides/tutorials/rest/)

### Maven Parent overrides

Due to Maven's design, elements are inherited from the parent POM to the project POM.
While most of the inheritance is fine, it also inherits unwanted elements like `<license>` and `<developers>` from the parent.
To prevent this, the project POM contains empty overrides for these elements.
If you manually switch to a different parent and actually want the inheritance, you need to remove those overrides.
