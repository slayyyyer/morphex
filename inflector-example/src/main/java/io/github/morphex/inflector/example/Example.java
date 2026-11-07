package io.github.morphex.inflector.example;

import io.github.morphex.inflector.CasePolicy;
import io.github.morphex.inflector.EnglishInflector;
import io.github.morphex.inflector.Inflection;
import io.github.morphex.inflector.Mode;
import java.util.List;

/**
 * Consumer example. Run with:
 *
 * <pre>./mvnw -q -pl inflector-example exec:java</pre>
 */
public final class Example {

    private Example() {
    }

    public static void main(String[] args) {
        EnglishInflector inflector = EnglishInflector.standard();

        System.out.println("== ordinary words ==");
        for (String noun : List.of("child", "city", "mouse", "cat", "analysis", "quiz", "blouse", "German")) {
            System.out.printf("%-12s -> %s%n", noun, inflector.pluralize(noun));
        }

        System.out.println();
        System.out.println("== alternatives and ambiguity ==");
        Inflection person = inflector.analyze("person");
        System.out.printf("person: preferred=%s alternatives=%s source=%s id=%s%n",
                person.plural(), person.alternatives(), person.source(), person.id());
        Inflection die = inflector.analyze("die");
        System.out.printf("die: preferred=%s alternatives=%s ambiguous=%s%n",
                die.plural(), die.alternatives(), die.ambiguous());

        System.out.println();
        System.out.println("== countability ==");
        for (String noun : List.of("information", "water", "sheep", "scissors")) {
            Inflection inf = inflector.analyze(noun);
            System.out.printf("%-12s -> %-12s countability=%s note=%s%n",
                    noun, inf.plural(), inf.countability(), inf.note());
        }

        System.out.println();
        System.out.println("== casing ==");
        System.out.println("CHILD   -> " + inflector.pluralize("CHILD"));
        System.out.println("iPhone  -> " + inflector.pluralize("iPhone"));
        System.out.println("UFO     -> " + inflector.pluralize("UFO"));
        System.out.println("SMS     -> " + inflector.pluralize("SMS"));

        System.out.println();
        System.out.println("== compounds ==");
        for (String noun : List.of("mother-in-law", "passer-by", "stand-in", "attorney general", "ice cream")) {
            System.out.printf("%-16s -> %-20s (%s)%n", noun, inflector.pluralize(noun),
                    inflector.analyze(noun).source());
        }

        System.out.println();
        System.out.println("== count-aware call and classical mode ==");
        System.out.println("1 child -> " + inflector.pluralize("child", 1));
        System.out.println("3 child -> " + inflector.pluralize("child", 3));
        EnglishInflector classical = EnglishInflector.builder().mode(Mode.CLASSICAL).build();
        System.out.println("cactus (anglicized) -> " + inflector.pluralize("cactus"));
        System.out.println("cactus (classical)  -> " + classical.pluralize("cactus"));

        System.out.println();
        System.out.println("== per-instance configuration ==");
        EnglishInflector custom = EnglishInflector.builder()
                .casePolicy(CasePolicy.UPPERCASE)
                .override("goose", "goosen")
                .initialism("XYZ")
                .build();
        System.out.println("goose (overridden) -> " + custom.pluralize("goose"));
        System.out.println("xyz (initialism)   -> " + custom.pluralize("XYZ"));
        System.out.println("shared engine is unchanged: " + inflector.pluralize("goose"));
    }
}
