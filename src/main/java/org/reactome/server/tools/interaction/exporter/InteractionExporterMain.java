package org.reactome.server.tools.interaction.exporter;

import com.martiansoftware.jsap.*;
import org.reactome.server.graph.domain.model.Species;
import org.reactome.server.graph.service.SpeciesService;
import org.reactome.server.graph.utils.ReactomeGraphCore;
import org.reactome.server.tools.interaction.exporter.filter.SimpleEntityPolicy;
import org.reactome.server.tools.interaction.exporter.util.InteractionExporterNeo4jConfig;
import org.reactome.server.tools.interaction.exporter.util.ProgressBar;
import org.reactome.server.tools.interaction.exporter.writer.InteractionWriter;
import org.reactome.server.tools.interaction.exporter.writer.Tab27Writer;
import org.reactome.server.tools.interaction.exporter.writer.TsvWriter;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class InteractionExporterMain {

	private static final String HOST = "host";
	private static final String PORT = "port";
	private static final String USER = "user";
	private static final String PASSWORD = "password";
	private static final String MAX_UNIT_SIZE = "maxUnitSize";
	private static final String SPECIES = "species";
	private static final String SIMPLE_ENTITIES_POLICY = "simpleEntitiesPolicy";
	private static final String OUTPUT = "output";
	private static final String OBJECT = "object";
	private static final String VERBOSE = "verbose";

	public static void main(String[] args) throws JSAPException {
		final Parameter[] parameters = {
				new FlaggedOption(HOST,                     JSAP.STRING_PARSER,     "bolt://localhost:7687",    JSAP.REQUIRED,      'h', HOST,                      "The neo4j host"),
				new FlaggedOption(USER,                     JSAP.STRING_PARSER,     "neo4j",            		JSAP.REQUIRED,      'u', USER,                      "The neo4j user"),
				new FlaggedOption(PASSWORD,                 JSAP.STRING_PARSER,     "neo4j",            		JSAP.REQUIRED,      'p', PASSWORD,                  "The neo4j password"),
				new FlaggedOption(MAX_UNIT_SIZE,            JSAP.INTEGER_PARSER,    "4",                		JSAP.NOT_REQUIRED,  'm', MAX_UNIT_SIZE,             "The maximum size of complexes/sets from which interactions are considered significant."),
				new FlaggedOption(SPECIES,                  JSAP.STRING_PARSER,     "Homo sapiens",     		JSAP.NOT_REQUIRED,  's', SPECIES,                   "1 or more species from which the interactions will be fetched. ALL to export all of the species").setAllowMultipleDeclarations(true),
				new FlaggedOption(OBJECT,                   JSAP.STRING_PARSER,     null,               		JSAP.NOT_REQUIRED,  'O', OBJECT,                    "Export interactions under these objects, species will be ignored").setAllowMultipleDeclarations(true),
				new FlaggedOption(SIMPLE_ENTITIES_POLICY,   JSAP.STRING_PARSER,     "NON_TRIVIAL",      		JSAP.NOT_REQUIRED,  't', SIMPLE_ENTITIES_POLICY,    "Set if simple entities are exported as well: ALL, NONE or NON_TRIVIAL."),
				new FlaggedOption(OUTPUT,                   JSAP.STRING_PARSER,     null,               		JSAP.REQUIRED,      'o', OUTPUT,                    "Prefix of the output files"),
				new QualifiedSwitch(VERBOSE,                JSAP.BOOLEAN_PARSER,    JSAP.NO_DEFAULT,    			JSAP.NOT_REQUIRED,  'v', VERBOSE,                   "Requests verbose output")
		};
		final SimpleJSAP jsap = new SimpleJSAP("Reactome interaction exporter", "Exports molecular interactions inferred from Reactome content",
				parameters);
		final JSAPResult config = jsap.parse(args);
		if (jsap.messagePrinted()) System.exit(1);

		final boolean verbose = config.getBoolean(VERBOSE);
		final int maxUnitSize = config.getInt(MAX_UNIT_SIZE);

		final String policy = config.getString(SIMPLE_ENTITIES_POLICY);
		final SimpleEntityPolicy simpleEntityPolicy = getSimpleEntityPolicy(policy);

		final String prefix = config.getString(OUTPUT);
		if (verbose) {
			System.out.println("prefix             = " + new File(prefix).getAbsolutePath());
			System.out.println("maxUnitSize        = " + maxUnitSize);
			System.out.println("simpleEntityPolicy = " + simpleEntityPolicy);
		}

		ReactomeGraphCore.initialise(config.getString(HOST), config.getString(USER), config.getString(PASSWORD), InteractionExporterNeo4jConfig.class);

		try (InteractionWriter tabWriter = new Tab27Writer(new FileOutputStream(prefix + ".psi-mitab.txt"));
		     InteractionWriter tsvWriter = new TsvWriter(new FileOutputStream(prefix + ".tab-delimited.txt"))) {
			final long start = System.nanoTime();
			final String[] objects = config.getStringArray(OBJECT);
			if (objects != null && objects.length > 0) {
				InteractionExporter.streamObjects(Arrays.asList(objects), simpleEntityPolicy, maxUnitSize, verbose)
						.forEach(interaction -> {
							tabWriter.write(interaction);
							tsvWriter.write(interaction);
						});
			} else {
				final String[] species = config.getStringArray(SPECIES);
				final List<String> speciesNames = getSpeciesNames(species);
				InteractionExporter.streamSpecies(speciesNames, simpleEntityPolicy, maxUnitSize, verbose)
						.forEach(interaction -> {
							tabWriter.write(interaction);
							tsvWriter.write(interaction);
						});
			}
			final long end = System.nanoTime();
			final long millis = (end - start) / 1_000_000;
			System.out.println();
			System.out.println(ProgressBar.formatTime(millis));
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static SimpleEntityPolicy getSimpleEntityPolicy(String policy) {
		final SimpleEntityPolicy simpleEntityPolicy;
		switch (policy.toLowerCase()) {
			case "all":
				simpleEntityPolicy = SimpleEntityPolicy.ALL;
				break;
			case "none":
				simpleEntityPolicy = SimpleEntityPolicy.NONE;
				break;
			case "non_trivial":
			default:
				simpleEntityPolicy = SimpleEntityPolicy.NON_TRIVIAL;
				break;
		}
		return simpleEntityPolicy;
	}

	/**
	 * If species is 'all', call the method {@link SpeciesService#getSpecies()}. Otherwise, call
	 * {@link SpeciesService#getSpecies(Object)} with every value in speciesArg and keep a list of unique species names.
	 *
	 * @return a list with the species names in Reactome database corresponding to the species passed by argument
	 */
	private static List<String> getSpeciesNames(String[] speciesArg) {
		final SpeciesService speciesService = ReactomeGraphCore.getService(SpeciesService.class);
		final List<String> species = new ArrayList<>();
		if (speciesArg.length == 1 && speciesArg[0].equalsIgnoreCase("all")) {
			for (Species specy : speciesService.getSpecies()) {
				species.add(specy.getDisplayName());
			}
		} else {
			for (String name : speciesArg) {
				species.add(speciesService.getSpecies(name).getDisplayName());
			}
		}
		return species;
	}

}
