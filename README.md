# univie-ufind-Parser

A CLI-Tool to get the next 10 Coursedates of a specified field of study on the university of Vienna

## Usage

`java -jar univie-ufind-Parser.jar [courseCode]`

e.g: `java -jar univie-ufind-Parser.jar 5.01` shows next 10 course dates for "Bachelor Informatik"

If you do not provide a courseCode it will try to read it from standard in.

You can also simply start the main function or use the IntelliJ `Run` configuration.

## Assumptions

- If there are multiple groups, only the dates of group 1 are considered
- If a lesson of a course has already started, it is not included
