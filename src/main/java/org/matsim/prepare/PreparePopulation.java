package org.matsim.prepare;

import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.*;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.scenario.ScenarioUtils;
import picocli.CommandLine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

import static org.matsim.run.RunDuesseldorfScenario.VERSION;

/**
 * Creates the population from the original input data.
 *
 * @author rakow
 */
@CommandLine.Command(
        name = "population",
        description = "Create population with input data",
        showDefaultValues = true
)
public class PreparePopulation implements Callable<Integer> {

    @CommandLine.Option(names = "--population", description = "Input original population file", required = true)
    private Path population;

    @CommandLine.Option(names = "--attributes", description = "Input person attributes file", required = true)
    private Path attributes;

    @CommandLine.Option(names = "--output", description = "Output folder", defaultValue = "scenarios/input")
    private Path output;

    public static void main(String[] args) {
        System.exit(new CommandLine(new PreparePopulation()).execute(args));
    }

    @Override
    public Integer call() throws Exception {

        Config config = ConfigUtils.createConfig();

        config.plans().setInputPersonAttributeFile(attributes.toString());
        config.plans().setInputFile(population.toString());

        config.plans().setInsistingOnUsingDeprecatedPersonAttributeFile(true);

        Scenario scenario = ScenarioUtils.loadScenario(config);

        Files.createDirectories(output);

        // Clear wrong coordinate system
        scenario.getPopulation().getAttributes().clear();

        scenario.getPopulation().getPersons().forEach((k, v) -> v.getAttributes().putAttribute("subpopulation", "person"));

        splitActivityTypesBasedOnDuration(scenario.getPopulation());

        PopulationUtils.writePopulation(scenario.getPopulation(), output.resolve("duesseldorf-" + VERSION + "-25pct.plans.xml.gz").toString());

        // sample 25% to 10%
        PopulationUtils.sampleDown(scenario.getPopulation(), 0.4);
        PopulationUtils.writePopulation(scenario.getPopulation(), output.resolve("duesseldorf-" + VERSION + "-10pct.plans.xml.gz").toString());

        // sample 10% to 1%
        PopulationUtils.sampleDown(scenario.getPopulation(), 0.1);
        PopulationUtils.writePopulation(scenario.getPopulation(), output.resolve("duesseldorf-" + VERSION + "-1pct.plans.xml.gz").toString());


        return 0;
    }

    /**
     * Split activities into typical durations to improve value of travel time savings calculation.
     *
     * @see playground.vsp.openberlinscenario.planmodification.CemdapPopulationTools
     */
    private void splitActivityTypesBasedOnDuration(Population population) {

        final double timeBinSize_s = 600.;

        // Calculate activity durations for the next step
        for (Person p : population.getPersons().values()) {
            for (Plan plan : p.getPlans()) {
                for (PlanElement el : plan.getPlanElements()) {

                    if (!(el instanceof Activity))
                        continue;

                    Activity act = (Activity) el;
                    double duration = act.getEndTime().orElse(24 * 3600)
                            - act.getStartTime().orElse(0);

                    int durationCategoryNr = (int) Math.round((duration / timeBinSize_s));

                    if (durationCategoryNr <= 0) {
                        durationCategoryNr = 1;
                    }

                    String newType = act.getType() + "_" + (durationCategoryNr * timeBinSize_s);
                    act.setType(newType);

                }

                mergeOvernightActivities(plan);
            }
        }
    }

    /**
     * See {@link playground.vsp.openberlinscenario.planmodification.CemdapPopulationTools}.
     */
    private void mergeOvernightActivities(Plan plan) {

        if (plan.getPlanElements().size() > 1) {
            Activity firstActivity = (Activity) plan.getPlanElements().get(0);
            Activity lastActivity = (Activity) plan.getPlanElements().get(plan.getPlanElements().size() - 1);

            String firstBaseActivity = firstActivity.getType().split("_")[0];
            String lastBaseActivity = lastActivity.getType().split("_")[0];

            if (firstBaseActivity.equals(lastBaseActivity)) {
                double mergedDuration = Double.parseDouble(firstActivity.getType().split("_")[1]) + Double.parseDouble(lastActivity.getType().split("_")[1]);


                firstActivity.setType(firstBaseActivity + "_" + mergedDuration);
                lastActivity.setType(lastBaseActivity + "_" + mergedDuration);
            }
        }  // skipping plans with just one activity

    }
}
