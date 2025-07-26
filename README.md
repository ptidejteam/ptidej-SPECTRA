# SPECTRA

![Java](https://img.shields.io/badge/Java-orange)
![Apache Maven](https://github.com/ptidejteam/ptidej-Ptidej/actions/workflows/maven.yml/badge.svg)
[![CO₂ Shield](https://img.shields.io/badge/CO₂-C_0.36g-C89806)](https://overbrowsing.com/projects/co2-shield)


## What is it?

Spectra is acronym for Software Performance Energy, Consumption, Time, Resources, & Analytics. It is created as part of research study to find correlational trade offs between the code efficiency and energy consumption of the software programs. 

## What do I need?

- Java 21
- Eclipse 2024-06 (4.32.0)
- Maven version 3.7.x (embedded in Eclipse)
- Postman (optional)

## How do I set it up?

- Open the terminal and check Java installation by typing "java --version". If Java is set up correctly, you should see the Java version printed on the terminal.
- Open Eclipse. On the left-side menu, select Import > Maven > Existing Maven Projects.
- Select project directory: tools4cities-middleware.
- Click "Finish" and wait for the project to load.
- After loading, right-click the "middleware" folder, and select Maven > Update Project.
- Right-click again and select: Run As > maven install
- Right-click again and select: Run As > maven test

Alternatively, if you have mvn set up in the command line, you can also run:

```bash
mvn dependency:purge-local-repository -DactTransitively=false -DreResolve=false
mvn clean
mvn validate
mvn install
```

## Who do I talk to?

Rushin Makwana (rushin.makwana@mail.concordia.ca)
