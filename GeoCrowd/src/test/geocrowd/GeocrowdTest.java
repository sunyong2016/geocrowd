/*******************************************************************************
 * @ Year 2013
 * This is the source code of the following papers. 
 * 
 * 1) Geocrowd: A Server-Assigned Crowdsourcing Framework. Hien To, Leyla Kazemi, Cyrus Shahabi.
 * 
 * 
 * Please contact the author Hien To, ubriela@gmail.com if you have any question.
 *
 * Contributors:
 * Hien To - initial implementation
 *******************************************************************************/
package test.geocrowd;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

import org.geocrowd.DatasetConstants;
import org.geocrowd.DatasetEnum;
import org.geocrowd.FoursquareConstants;
import org.geocrowd.Geocrowd;
import org.geocrowd.GeocrowdInstance;
import org.geocrowd.AlgorithmEnum;
import org.geocrowd.GeocrowdOnline;
import org.geocrowd.ArrivalRateEnum;
import org.geocrowd.common.crowd.ExpertWorker;
import org.geocrowd.common.crowd.WorkingRegion;
import org.geocrowd.common.utils.TaskUtility;
import org.geocrowd.common.utils.Utils;
import org.geocrowd.datasets.params.GeocrowdConstants;
import org.geocrowd.datasets.params.GowallaConstants;
import org.geocrowd.datasets.synthetic.ArrivalRateGenerator;
import org.geocrowd.dtype.Point;
import org.geocrowd.matching.OnlineBipartiteMatching;
import org.junit.Test;

// TODO: Auto-generated Javadoc
/**
 * The Class GeocrowdTest.
 */
public class GeocrowdTest {

	public static void main(String[] args) {
		GeocrowdTest geoCrowdTest = new GeocrowdTest();
		switch (Geocrowd.DATA_SET) {
		case GOWALLA:
			geoCrowdTest.testGenerateGowallaTasks();
			break;
		case FOURSQUARE:
			geoCrowdTest.testGenerateFoursquareTasks();
			break;
		}
	}

	@Test
	public void testGenerateFoursquareTasks() {
		Geocrowd.DATA_SET = DatasetEnum.FOURSQUARE;
		GeocrowdInstance geoCrowd = new GeocrowdInstance();
		// geoCrowd.printBoundaries();
		// geoCrowd.createGrid();
		// geoCrowd.readEntropy();
		// System.out.println("entropy list size: " +
		// geoCrowd.entropyList.size());

		ArrayList<Integer> taskCounts = ArrivalRateGenerator.generateCounts(
				GeocrowdConstants.TIME_INSTANCE, 1000, ArrivalRateEnum.CONSTANT);
		ArrayList<Point> venues = readFoursquareVenues("dataset/real/foursquare/venue_locs.txt");
		for (int i = 0; i < GeocrowdConstants.TIME_INSTANCE; i++) {
			geoCrowd.readTasksFoursquare(taskCounts.get(i),
					FoursquareConstants.foursquareTaskFileNamePrefix + i
							+ ".txt", venues);
			geoCrowd.TimeInstance++;
		}
	}

	private ArrayList<Point> readFoursquareVenues(String filename) {
		FileReader reader;
		ArrayList<Point> points = new ArrayList<Point>();
		try {
			reader = new FileReader(filename);
			BufferedReader in = new BufferedReader(reader);
			StringBuffer sb = new StringBuffer();

			while (in.ready()) {
				String line = in.readLine();
				String[] parts = line.split("\\s");
				Double lat = Double.parseDouble(parts[0]);
				Double lng = Double.parseDouble(parts[1]);
				points.add(new Point(lat, lng));

			}
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (NumberFormatException | IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return points;
	}

	/**
	 * Test geocrowd.
	 */
	@Test
	public void testGeoCrowd() {
		int totalScore = 0;
		double totalAssignedTasks = 0;
		double totalExpertiseAssignedTasks = 0;
		long totalTime = 0;
		double totalSumDist = 0;
		double avgAvgWT = 0;
		double avgVarWT = 0;
		double totalAvgWT = 0;
		double totalVARWT = 0;

		for (int k = 0; k < 1; k++) {

			System.out.println("+++++++ Iteration: " + (k + 1));
			Geocrowd.DATA_SET = DatasetEnum.GOWALLA;
			Geocrowd.algorithm = AlgorithmEnum.ONLINE;
			GeocrowdInstance geoCrowd = new GeocrowdInstance();
			geoCrowd.printBoundaries();
			geoCrowd.createGrid();
			geoCrowd.readEntropy();
			for (int i = 0; i < GeocrowdConstants.TIME_INSTANCE; i++) {
				System.out.println("---------- Time instance: " + (i + 1));
				geoCrowd.readTasks(Utils.datasetToTaskPath(Geocrowd.DATA_SET)
						+ i + ".txt");
				geoCrowd.readWorkers(Utils
						.datasetToWorkerPath(Geocrowd.DATA_SET) + i + ".txt");

				geoCrowd.matchingTasksWorkers();

				// debug
				System.out.println("#Tasks: " + geoCrowd.taskList.size());
				System.out.println("#Workers: " + geoCrowd.workerList.size());
				System.out.println("scheduling...");
				double startTime = System.nanoTime();
				geoCrowd.maxWeightedMatching();
				double runtime = (System.nanoTime() - startTime) / 1000000000.0;
				totalTime += runtime;
				System.out.println("Time: " + runtime);
				geoCrowd.TimeInstance++;
			}

			System.out.println("*************SUMMARY ITERATION " + (k + 1)
					+ " *************");
			System.out.println("#Total workers: " + geoCrowd.WorkerCount);
			System.out.println("#Total tasks: " + geoCrowd.TaskCount);
			totalScore += geoCrowd.TotalScore;
			totalAssignedTasks += geoCrowd.TotalAssignedTasks;
			totalExpertiseAssignedTasks += geoCrowd.TotalTasksExpertiseMatch;
			totalSumDist += geoCrowd.TotalTravelDistance;

			double avgScore = ((double) totalScore) / (k + 1);
			double avgAssignedTasks = (totalAssignedTasks)
					/ ((k + 1) * GeocrowdConstants.TIME_INSTANCE);
			double avgExpertiseAssignedTasks = (totalExpertiseAssignedTasks)
					/ ((k + 1) * GeocrowdConstants.TIME_INSTANCE);
			long avgTime = (totalTime)
					/ ((k + 1) * GeocrowdConstants.TIME_INSTANCE);
			System.out.println("Total score: " + totalScore
					+ "   # of rounds: " + (k + 1) + "  avg: " + avgScore);
			System.out.println("Total assigned taskes: " + totalAssignedTasks
					+ "   # of rounds:" + (k + 1) + "  avg: "
					+ avgAssignedTasks);
			System.out.println("Total expertise matches: "
					+ totalExpertiseAssignedTasks + "   # of rounds: "
					+ (k + 1) + "  avg: " + avgExpertiseAssignedTasks);
			System.out.println("Total time: " + totalTime + "   # of rounds: "
					+ (k + 1) + "  avg time:" + avgTime);
			double avgDist = totalSumDist / totalAssignedTasks;
			System.out.println("Total distances: " + totalSumDist
					+ "   # of rounds: " + (k + 1) + "  avg: " + avgDist);

			avgAvgWT = totalAvgWT / ((k + 1) * GeocrowdConstants.TIME_INSTANCE);
			avgVarWT = totalVARWT / ((k + 1) * GeocrowdConstants.TIME_INSTANCE);
			System.out.println("Average worker per task: " + avgAvgWT
					+ "   with variance: " + avgVarWT);
		} // end of for loop
	}

	@Test
	public void testGeocrowdOnline() {
		double totalAssignedTasks = 0;
		long totalTime = 0;
		double totalSumDist = 0;
		double avgAvgWT = 0;
		double avgVarWT = 0;
		double totalAvgWT = 0;
		double totalVARWT = 0;

		for (int k = 0; k < 1; k++) {

			System.out.println("+++++++ Iteration: " + (k + 1));
			Geocrowd.DATA_SET = DatasetEnum.GOWALLA;
			Geocrowd.algorithm = AlgorithmEnum.BASIC;
			GeocrowdOnline geoCrowd = new GeocrowdOnline(
					"dataset/real/gowalla/worker/gowalla_workers0.txt");
			for (int i = 0; i < GeocrowdConstants.TIME_INSTANCE; i++) {
				System.out.println("---------- Time instance: " + (i + 1));
				geoCrowd.readTasks(Utils.datasetToTaskPath(Geocrowd.DATA_SET)
						+ i + ".txt");
				geoCrowd.readWorkers(Utils
						.datasetToWorkerPath(Geocrowd.DATA_SET) + i + ".txt");

				geoCrowd.matchingTasksWorkers();

				// debug
				System.out.println("#Tasks: " + geoCrowd.taskList.size());
				System.out.println("#Workers: " + geoCrowd.workerList.size());
				System.out.println("scheduling...");
				double startTime = System.nanoTime();

				geoCrowd.onlineMatching();

				double runtime = (System.nanoTime() - startTime) / 1000000000.0;
				totalTime += runtime;
				System.out.println("Time: " + runtime);
				geoCrowd.TimeInstance++;
			}

			System.out.println("*************SUMMARY ITERATION " + (k + 1)
					+ " *************");
			System.out.println("#Total workers: " + geoCrowd.WorkerCount);
			System.out.println("#Total tasks: " + geoCrowd.TaskCount);
			totalAssignedTasks += geoCrowd.TotalAssignedTasks;
			totalSumDist += geoCrowd.TotalTravelDistance;

			double avgAssignedTasks = (totalAssignedTasks)
					/ ((k + 1) * GeocrowdConstants.TIME_INSTANCE);
			long avgTime = (totalTime)
					/ ((k + 1) * GeocrowdConstants.TIME_INSTANCE);
			System.out.println("Total assigned taskes: " + totalAssignedTasks
					+ "   # of rounds:" + (k + 1) + "  avg: "
					+ avgAssignedTasks);
			System.out.println("Total time: " + totalTime + "   # of rounds: "
					+ (k + 1) + "  avg time:" + avgTime);
			double avgDist = totalSumDist / totalAssignedTasks;
			System.out.println("Total distances: " + totalSumDist
					+ "   # of rounds: " + (k + 1) + "  avg: " + avgDist);

			avgAvgWT = totalAvgWT / ((k + 1) * GeocrowdConstants.TIME_INSTANCE);
			avgVarWT = totalVARWT / ((k + 1) * GeocrowdConstants.TIME_INSTANCE);
			System.out.println("Average worker per task: " + avgAvgWT
					+ "   with variance: " + avgVarWT);
		} // end of for loop
	}

	/**
	 * Test generate gowalla tasks.
	 */
	@Test
	public void testGenerateGowallaTasks() {
		Geocrowd.DATA_SET = DatasetEnum.GOWALLA;
		GeocrowdInstance geoCrowd = new GeocrowdInstance();
		geoCrowd.printBoundaries();
		geoCrowd.createGrid();
		geoCrowd.readEntropy();
		// System.out.println("entropy list size: " +
		// geoCrowd.entropyList.size());
		ArrayList<Integer> taskCounts = ArrivalRateGenerator.generateCounts(
				GeocrowdConstants.TIME_INSTANCE*10, 1000, ArrivalRateEnum.CONSTANT);
		for (int i : taskCounts)
			System.out.println(i);
		for (int i = 0; i < GeocrowdConstants.TIME_INSTANCE*10; i++) {
			
			//test randomly generated without replacement
			geoCrowd.readTasksWithEntropy3(taskCounts.get(i),
					GowallaConstants.gowallaTaskFileNamePrefix + i + ".txt");
			geoCrowd.TimeInstance++;
		}
	}

	/**
	 * Test generate yelp workers.
	 */
	@Test
	public void testGenerateYelpWorkers() {
		Geocrowd.DATA_SET = DatasetEnum.YELP;
		GeocrowdInstance geoCrowd = new GeocrowdInstance();
		geoCrowd.printBoundaries();
		geoCrowd.readWorkers("dataset/real/yelp/worker/yelp_workers0.txt");
		try {
			// create whole path automatically if not exist
			String outputFile = "dataset/real/yelp/yelp.dat";
			Path pathToFile = Paths.get(outputFile);
			Files.createDirectories(pathToFile.getParent());
			
			FileWriter writer = new FileWriter(outputFile);
			BufferedWriter out = new BufferedWriter(writer);

			StringBuffer sb = new StringBuffer();
			double sum = 0;
			int count = 0;
			double maxMBR = 0;
			for (int i = 0; i < geoCrowd.workerList.size(); i++) {
				ExpertWorker w = (ExpertWorker) geoCrowd.workerList.get(i);
				sb.append(w.getLat() + "\t" + w.getLng() + "\n");
				double d = TaskUtility.diagonalLength(w.getMbr());
				sum += d;
				count++;
				if (d > maxMBR)
					maxMBR = d;
			}

			out = new BufferedWriter(writer);
			out.write(sb.toString());
			out.close();
			WorkingRegion mbr = new WorkingRegion(geoCrowd.minLatitude, geoCrowd.minLongitude,
					geoCrowd.maxLatitude, geoCrowd.maxLongitude);
			System.out.println("Region MBR size: " + TaskUtility.diagonalLength(mbr));

			System.out.println("Area: " + TaskUtility.areaGIS(mbr));
			System.out.println("Number of users: " + count);
			System.out.println("Average users' MBR size: " + sum / count);
			System.out.println("Max users' MBR size: " + maxMBR);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/**
	 * Test.
	 */
	@Test
	public void testGeocrowdOnline_small() {
		ArrayList<Integer> workers = new ArrayList<>();

		workers.add(new Integer(10));
		workers.add(new Integer(11));
		workers.add(new Integer(12));
		workers.add(new Integer(13));

		OnlineBipartiteMatching obm = new OnlineBipartiteMatching(workers);

		HashMap<Integer, ArrayList> container = new HashMap<>();
		container.put(0, new ArrayList<Integer>(Arrays.asList(10)));
		container.put(1, new ArrayList<Integer>(Arrays.asList(10)));
		container.put(2, new ArrayList<Integer>(Arrays.asList(11, 12)));

		System.out.println(obm.onlineMatching(container));

		container = new HashMap<>();
		container.put(0, new ArrayList<Integer>(Arrays.asList(11)));
		container.put(1, new ArrayList<Integer>(Arrays.asList(13)));
		container.put(2, new ArrayList<Integer>(Arrays.asList(10)));

		System.out.println(obm.onlineMatching(container));
	}

	/**
	 * Test geo crowd_ small.
	 */
	@Test
	public void testGeoCrowd_Small() {
		Geocrowd.DATA_SET = DatasetEnum.SMALL_TEST;
		Geocrowd.algorithm = AlgorithmEnum.BASIC;
		GeocrowdInstance geoCrowd = new GeocrowdInstance();
		geoCrowd.printBoundaries();
		geoCrowd.createGrid();
		// geoCrowd.readEntropy();

		geoCrowd.readWorkers(DatasetConstants.smallWorkerFileNamePrefix
				+ "0.txt");
		geoCrowd.readTasks(DatasetConstants.smallTaskFileNamePrefix + "0.txt");
		geoCrowd.matchingTasksWorkers();
		geoCrowd.computeAverageTaskPerWorker();
		System.out.println("avgTW " + geoCrowd.avgTW);
		System.out.println("varTW " + geoCrowd.varTW);

		geoCrowd.computeAverageWorkerPerTask();
		System.out.println("avgWT " + geoCrowd.avgWT);
		System.out.println("varWT " + geoCrowd.varWT);

		geoCrowd.maxWeightedMatching();

		System.out.println("Score: " + geoCrowd.TotalScore);
		System.out.println("Assigned task: " + geoCrowd.TotalAssignedTasks);
		System.out.println("Exact assigned task: "
				+ geoCrowd.TotalTasksExpertiseMatch);
	}
}