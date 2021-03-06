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
package org.geocrowd;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import org.geocrowd.common.crowd.WTMatch;
import org.geocrowd.common.crowd.ExpertTask;
import org.geocrowd.common.crowd.ExpertWorker;
import org.geocrowd.common.crowd.TaskFactory;
import org.geocrowd.common.crowd.WorkerFactory;
import org.geocrowd.common.crowd.WorkingRegion;
import org.geocrowd.common.entropy.EntropyRecord;
import org.geocrowd.common.utils.TaskUtility;
import org.geocrowd.common.utils.Utils;
import org.geocrowd.cplex.BPMatchingCplex;
import org.geocrowd.dtype.Cell;
import org.geocrowd.dtype.Point;
import org.geocrowd.dtype.Range;
import org.geocrowd.datasets.params.GeocrowdConstants;
import org.geocrowd.datasets.params.GowallaConstants;
import org.geocrowd.datasets.synthesis.gowalla.GowallaProcessor;
import org.geocrowd.datasets.synthetic.Parser;
import org.geocrowd.datasets.synthetic.UniformGenerator;
import org.geocrowd.matching.Hungarian;
import org.geocrowd.matching.OnlineBipartiteMatching;
import org.geocrowd.matching.Utility;

// TODO: Auto-generated Javadoc
/**
 * The Class Geocrowd.
 * 
 * @author Leyla
 */
public class GeocrowdInstance extends Geocrowd {

	/** The worker no. */
	public int workerNo = 100; // 100 // number of workers when workers are to
								// be generated #
	/** The grid. */
	public Cell[][] grid;

	/** The sum max t. */
	public int sumMaxT = 0;

	/** The Total score. */
	public double TotalScore = 0;

	/** The Total tasks expertise match. */
	public int TotalTasksExpertiseMatch = 0; // number of assigned tasks, from
												// exact
												// match

	/** The task expired no. */
	public int taskExpiredNo = 0;

	/** The all tasks. */
	public ArrayList<double[]> allTasks = new ArrayList();

	/**
	 * Instantiates a new geocrowd.
	 */
	public GeocrowdInstance() {
		GowallaProcessor prep = new GowallaProcessor(GeocrowdConstants.TIME_INSTANCE, WorkerType.EXPERT,
				TaskType.EXPERT, TaskCategoryEnum.RANDOM);
		prep.readBoundary();
		minLatitude = GowallaProcessor.minLat;
		maxLatitude = GowallaProcessor.maxLat;
		minLongitude = GowallaProcessor.minLng;
		maxLongitude = GowallaProcessor.maxLng;
	}

	/**
	 * Check boundary mbr.
	 * 
	 * @param mbr
	 *            the mbr
	 */
	private void checkBoundaryMBR(WorkingRegion mbr) {
		if (mbr.getMinLat() < minLatitude)
			mbr.setMinLat(minLatitude);
		if (mbr.getMaxLat() > maxLatitude)
			mbr.setMaxLat(maxLatitude);
		if (mbr.getMinLng() < minLongitude)
			mbr.setMinLng(minLongitude);
		if (mbr.getMaxLng() > maxLongitude)
			mbr.setMaxLng(maxLongitude);
	}

	// compute score of a tuple <w,t>
	/**
	 * Compute score.
	 * 
	 * @param w
	 *            the w
	 * @param t
	 *            the t
	 * @return the double
	 */
	private double computeScore(ExpertWorker w, ExpertTask t) {
		if (TaskUtility.isExactMatch(w, t))
			return Constants.EXPERTISE_MATCH_SCORE;
		else
			return Constants.NON_EXPERTISE_MATCH_SCORE;
	}

	/**
	 * Randomly generate task (without entropy).
	 * 
	 * @param fileName
	 *            the file name
	 */
	public void generateTasksWOEntropy(String fileName) {
		int listCount = taskList.size();
		try {
			// create whole path automatically if not exist
			Path pathToFile = Paths.get(fileName);
			Files.createDirectories(pathToFile.getParent());
			
			FileWriter writer = new FileWriter(fileName);
			BufferedWriter out = new BufferedWriter(writer);
			for (int i = 0; i < GeocrowdConstants.TASK_NUMBER; i++) {
				double lat = UniformGenerator.randomValue(new Range(
						minLatitude, maxLatitude), false);
				double lng = UniformGenerator.randomValue(new Range(
						minLongitude, maxLongitude), false);
				// printBoundaries();
				// System.out.println(lat + " " + lng);
				int taskCategory = (int) UniformGenerator.randomValue(
						new Range(0, GeocrowdConstants.TASK_CATEGORY_NUMBER),
						true);
				ExpertTask t = (ExpertTask) TaskFactory.getTask(
						TaskType.EXPERT, lat, lng);
				t.setArrivalTime(TimeInstance);
				t.setEntropy(-1);
				t.setCategory(taskCategory);

				out.write(lat + "," + lng + "," + time + "," + (-1) + "\n");
				taskList.add(listCount, t);
				listCount++;

			}
			TaskCount += GeocrowdConstants.TASK_NUMBER;
			System.out.println("#Total tasks:" + TaskCount);
			out.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Randomly generate workers. synthetic dataset
	 * 
	 * @param fileName
	 *            : output
	 * 
	 * @maxT is randomly generated
	 * @MBR's size is randomly generated
	 */
	public void generateWorkersRandomMBR(String fileName) {
		workerList = new ArrayList();
		double maxRangeX = (maxLatitude - minLatitude)
				* GeocrowdConstants.MAX_WORKING_REGION;
		double maxRangeY = (maxLongitude - minLongitude)
				* GeocrowdConstants.MAX_WORKING_REGION;
		try {
			// create whole path automatically if not exist
			Path pathToFile = Paths.get(fileName);
			Files.createDirectories(pathToFile.getParent());
			
			FileWriter writer = new FileWriter(fileName);
			BufferedWriter out = new BufferedWriter(writer);

			for (int i = 0; (i < workerNo); i++) {
				double randomEntropy = UniformGenerator.randomValue(new Range(
						0, totalEntropy), false);
				double sum = 0;
				int randomIdx = -1;
				for (int j = 0; j < entropyList.size(); j++) {
					EntropyRecord dR = entropyList.get(j);
					if (randomEntropy < sum) {
						randomIdx = j;
						break;
					}
					sum += dR.getEntropy();
				}
				EntropyRecord dR = entropyList.get(randomIdx);
				int row = dR.getCoord().getRowId();
				double startLat = rowToLat(row);
				double endLat = rowToLat(row + 1);
				double lat = UniformGenerator.randomValue(new Range(startLat,
						endLat), false);
				int col = dR.getCoord().getColId();
				double startLng = colToLng(col);
				double endLng = colToLng(col + 1);
				double lng = UniformGenerator.randomValue(new Range(startLng,
						endLng), false);
				int maxT = (int) UniformGenerator.randomValue(new Range(0,
						GeocrowdConstants.MAX_WORKER_CAPACITY), true) + 1;
				double rangeX = UniformGenerator.randomValue(new Range(0,
						maxRangeX), false);
				double rangeY = UniformGenerator.randomValue(new Range(0,
						maxRangeY), false);
				WorkingRegion mbr = WorkingRegion.createMBR(lat, lng, rangeX,
						rangeY);
				checkBoundaryMBR(mbr);
				int exp = (int) UniformGenerator.randomValue(new Range(0,
						GeocrowdConstants.TASK_CATEGORY_NUMBER), true);
				ExpertWorker w = (ExpertWorker) WorkerFactory.getWorker(
						WorkerType.EXPERT, lat, lng);
				w.setId("dump");
				w.setCapacity(maxT);
				w.setMbr(mbr);
				w.addExpertise(exp);
				out.write(randomIdx + ",time" + "," + lat + "," + lng + ","
						+ maxT + "," + "[" + mbr.getMinLat() + ","
						+ mbr.getMinLng() + "," + mbr.getMaxLat() + ","
						+ mbr.getMaxLng() + "]\n");
				workerList.add(w);
				dR.incUserCount();
			}
			WorkerCount += workerNo;
			out.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Compute which tasks within working region of which worker and vice versa.
	 */
	@Override
	public void matchingTasksWorkers() {
		invertedContainer = new HashMap<Integer, ArrayList<Integer>>();
		candidateTaskIndices = new ArrayList();
		taskSet = new HashSet<Integer>();
		containerWorker = new ArrayList<ArrayList>();
		containerPrune = new ArrayList[workerList.size()];

		// remove expired task from task list
		pruneExpiredTasks();

		for (int idx = 0; idx < workerList.size(); idx++) {
			ExpertWorker w = (ExpertWorker) workerList.get(idx);
			rangeQuery(idx, w);
		}

		// remove workers with no tasks
		sumMaxT = 0;
		for (int i = containerPrune.length - 1; i >= 0; i--) {
			if (containerPrune[i] == null || containerPrune[i].size() == 0) {
				/* remove from worker list */
				workerList.remove(i);
			} else
				sumMaxT += workerList.get(i).getCapacity();
		}

		for (int i = 0; i < containerPrune.length; i++) {
			if (containerPrune[i] != null && containerPrune[i].size() > 0)
				/* add non-empty elements to containerWorker */
				containerWorker.add(containerPrune[i]);
		}

		System.out.println();
	}

	// any number of maxT
	/**
	 * Max weighted matching.
	 * 
	 * @return the double
	 */
	public double maxWeightedMatching() {

		if (sumMaxT == 0 || candidateTaskIndices.size() == 0) {
			System.out.println("No scheduling");
			return 0;
		}

		if (algorithm == AlgorithmEnum.ONLINE)
			return onlineMatching();

		// sumMaxT is the number of logical workers
		double[][] array = new double[sumMaxT][candidateTaskIndices.size()]; // row
		// represents
		// workers,
		// column
		// represents
		// tasks
		HashMap<Integer, Integer> logicalWorkerToWorker = new HashMap<Integer, Integer>();
		int row = 0;
		for (int i = 0; i < containerWorker.size(); i++) {
			ArrayList<Integer> tasks = containerWorker.get(i);
			if (tasks != null)
				for (int j : tasks) {
					array[row][j] = computeScore(
							(ExpertWorker) workerList.get(i),
							(ExpertTask) taskList.get(candidateTaskIndices
									.get(j)));
				}
			logicalWorkerToWorker.put(row, i);
			row++;
			// create logical workers
			ExpertWorker w = (ExpertWorker) workerList.get(i);
			for (int j = 0; j < w.getCapacity() - 1; j++) {
				array[row] = Arrays.copyOf(array[row - 1],
						array[row - 1].length);
				logicalWorkerToWorker.put(row, i);
				row++;
			}
		}

		// if (sumMaxT != row)
		// System.out.println("problem!!!!!");
		// System.out.println("row" + row);

		double[][] origin = null;
		// save original scores
		// if (this.algorithm == AlgoEnums.GR) {
		origin = Utility.copyOf(array);
		// } else {
		// AlgoEnums temp = this.algorithm;
		// this.algorithm = AlgoEnums.GR;
		// row = 0;
		// origin = new double[sumMaxT][candidateTasks.size()];
		// for (int i = 0; i < _container.size(); i++) {
		// ArrayList<Integer> tasks = _container.get(i);
		// if (tasks != null)
		// for (int j : tasks) {
		// origin[row][j] = computeScore(workerList.get(i),
		// taskList.get(candidateTasks.get(j)));
		// }
		// row++;
		// // create logical workers
		// Worker w = workerList.get(i);
		// for (int j = 0; j < w.getMaxTaskNo() - 1; j++) {
		// origin[row] = Arrays.copyOf(origin[row - 1],
		// origin[row - 1].length);
		// row++;
		// }
		// }
		// this.algorithm = temp;
		// }

		// transpose the matrix if #workers < #tasks
		boolean isTranpose = false;
		if (array.length > array[0].length) {
			// Array transposed (because rows(worker)>columns(task))
			array = Utility.transpose(array);
			origin = Utility.transpose(origin);
			isTranpose = true;
		}

		// Utility.print2(array);

		for (int i = 0; i < array.length; i++) // Generate cost by subtracting.
		{
			for (int j = 0; j < array[i].length; j++) {
				array[i][j] = -array[i][j];
			}
		}

		Hungarian HA = new Hungarian(array);
		int[] r = HA.execute(array);

		// COMPUTE
		double totalScore = 0;
		int totalTasksAssigned = 0;
		int totalTasksExactMatch = 0;
		double totalDistance = 0;

		for (int i = r.length - 1; i >= 0; i--)
			if (origin[i][r[i]] > 0)
				totalScore += origin[i][r[i]];

		// find max matching with minimum cost (if possible)
		ArrayList<WTMatch> taskAssigned = null;

		if (algorithm == AlgorithmEnum.LLEP || algorithm == AlgorithmEnum.NNP) {
			// coefficient values are generated from entropies or distance
			List<Double> objectiveCoeff = new ArrayList<Double>();
			// generated from scores<worker,task>
			List<Double> matchingCoeff = new ArrayList<Double>();
			// mapping between variable and edges
			HashMap<Integer, WTMatch> mapColToMatch = new HashMap<Integer, WTMatch>();

			// map between indices of logical workers to workers
			// HashMap<Integer, Integer> logicalWorkerToWorker = new
			// HashMap<Integer, Integer>();
			int var = 0;
			int w = 0; // physical worker
			int numWorker = 0; // logical worker
			for (ArrayList<Integer> tasks : containerWorker) {
				ExpertWorker worker = (ExpertWorker) workerList.get(w);
				if (tasks != null) {
					for (int i = 0; i < worker.getCapacity(); i++) {
						for (int t : tasks) {
							mapColToMatch.put(var++, new WTMatch(numWorker, t));
							switch (algorithm) {
							case LLEP:
								objectiveCoeff.add(computeCost(taskList
										.get(candidateTaskIndices.get(t))));
								break;
							case NNP:
								objectiveCoeff
										.add(GeocrowdTaskUtility.distanceWorkerTask(
												DATA_SET,
												worker,
												taskList.get(candidateTaskIndices
														.get(t))));
								break;
							}
							matchingCoeff.add(computeScore(worker,
									(ExpertTask) taskList
											.get(candidateTaskIndices.get(t))));
						}
						// logicalWorkerToWorker.put(numWorker, w);
						numWorker++;
					}
				}
				w++;
			}

			BPMatchingCplex cplex = new BPMatchingCplex(numWorker,
					candidateTaskIndices.size(), objectiveCoeff, matchingCoeff,
					totalScore, mapColToMatch);
			taskAssigned = cplex.maxMatchingMinCost();

			// recompute the maximum matching to make sure the cplex solver is
			// correct
			double _totalScore = 0;
			Iterator<WTMatch> it = taskAssigned.iterator();
			while (it.hasNext()) {
				WTMatch pair = it.next();
				ExpertWorker worker = (ExpertWorker) workerList
						.get(logicalWorkerToWorker.get(pair.getW()));
				ExpertTask task = (ExpertTask) taskList
						.get(candidateTaskIndices.get(pair.getT()));
				double score = computeScore(worker, task);
				totalDistance += GeocrowdTaskUtility.distanceWorkerTask(
						DATA_SET, worker, task);
				_totalScore += score;
				// if (score == Constants.EXACT_MATCH_SCORE)
				// totalTasksExactMatch++;
				// exact match?
				if (TaskUtility.isExactMatch(worker, task))
					totalTasksExactMatch++;
			}
			totalTasksAssigned = taskAssigned.size();

			if (_totalScore != totalScore)
				System.out
						.println("Error: mismatch between Hungarian & Cplex; total score: "
								+ totalScore + " .vs " + _totalScore);
		}

		// the solved task from task list
		ArrayList<Integer> solvedTasks = new ArrayList(); // indices
															// in
															// candidateTasks

		if (algorithm == AlgorithmEnum.BASIC) {
			// remove the assigned task
			for (int i = r.length - 1; i >= 0; i--) {
				if (origin[i][r[i]] > 0) {
					// the task i is assigned
					totalTasksAssigned++;

					// exact match?
					// if (origin[i][r[i]] == Constants.EXACT_MATCH_SCORE)
					// totalTasksExactMatch++;

					if (isTranpose) {
						solvedTasks.add(candidateTaskIndices.get(i));
						ExpertWorker worker = (ExpertWorker) workerList
								.get(logicalWorkerToWorker.get(r[i]));
						ExpertTask task = (ExpertTask) taskList
								.get(candidateTaskIndices.get(i));
						totalDistance += GeocrowdTaskUtility
								.distanceWorkerTask(DATA_SET, worker, task);
						// exact match?
						if (TaskUtility.isExactMatch(worker, task))
							totalTasksExactMatch++;
					} else {
						solvedTasks.add(candidateTaskIndices.get(r[i]));
						ExpertWorker worker = (ExpertWorker) workerList
								.get(logicalWorkerToWorker.get(i));
						ExpertTask task = (ExpertTask) taskList
								.get(candidateTaskIndices.get(r[i]));
						totalDistance += GeocrowdTaskUtility
								.distanceWorkerTask(DATA_SET, worker, task);
						// exact match?
						if (TaskUtility.isExactMatch(worker, task))
							totalTasksExactMatch++;
					}
				}
			}
			// remove the assigned task
			Collections.sort(solvedTasks);// to remove the last task first
			for (int i = solvedTasks.size() - 1; i >= 0; i--) {
				taskList.remove((int) solvedTasks.get(i));
			}
		} else if (algorithm == AlgorithmEnum.LLEP
				|| algorithm == AlgorithmEnum.NNP) {
			if (taskAssigned != null) {
				for (int i = 0; i < taskAssigned.size(); i++) {
					WTMatch pair = taskAssigned.get(i);
					solvedTasks.add(candidateTaskIndices.get(pair.getT()));
				}
				Collections.sort(solvedTasks);
				for (int i = solvedTasks.size() - 1; i >= 0; i--) {
					taskList.remove((int) solvedTasks.get(i));
				}
			}
		}

		TotalScore += totalScore;
		TotalAssignedTasks += totalTasksAssigned;
		TotalTasksExpertiseMatch += totalTasksExactMatch;
		TotalTravelDistance += totalDistance;

		System.out.printf("Maximum score: %.2f\n", totalScore);
		System.out.println("#Assigned tasks: " + totalTasksAssigned);
		System.out.println("#Exact assigned tasks: " + totalTasksExactMatch);
		System.out.println("#Travel distance: " + totalDistance);

		// check correctness
		if (TotalExpiredTask + taskList.size() + TotalAssignedTasks != TaskCount) {
			System.out.println("Logic error!!!");
			System.out.println("#Expired tasks: " + TotalExpiredTask);
			System.out.println("#Remained tasks: " + taskList.size());
			System.out.println("#Assigned tasks: " + taskList.size());
			System.out.println("#Task count: " + TaskCount);
		}

		return totalScore;
	}

	// this methods compute max weighted matching using the second Hungarian
	// algorithm with heuristics, thus faster than the other one
	// assuming maxT = 1 for all workers
	/**
	 * Max weighted matching2.
	 * 
	 * @return the double
	 */
	public double maxWeightedMatching2() {
		double[][] array = new double[containerWorker.size()][taskList.size()]; // row
		// represents
		// workers,
		// column
		// represents
		// tasks
		for (int i = 0; i < containerWorker.size(); i++) {
			ArrayList<Integer> tasks = containerWorker.get(i);
			if (tasks != null)
				for (int j : tasks) {
					array[i][j] = computeScore(
							(ExpertWorker) workerList.get(i),
							(ExpertTask) taskList.get(j));
					// if (array[i][j] != 0)
					// System.out.println(array[i][j]);
				}
		}

		// transpose the matrix if #workers < #tasks
		if (array.length > array[0].length) {
			// Array transposed (because rows(worker)>columns(task))
			array = Utility.transpose(array);
		}

		double[][] origin = Utility.copyOf(array);

		for (int i = 0; i < array.length; i++) // Generate cost by subtracting.
		{
			for (int j = 0; j < array[i].length; j++) {
				array[i][j] = -array[i][j];
			}
		}

		// Utility.print2(origin);
		Hungarian HA = new Hungarian(array);
		int[] r = HA.execute(array);
		double sum = 0;

		int totalTasksAssigned = 0;
		int totalTasksExactMatch = 0;
		ArrayList<Integer> solvedTasks = new ArrayList<Integer>();
		for (int i = r.length - 1; i >= 0; i--) {
			// System.out.println((i + 1) + "->" + (r[i] + 1) + " : "
			// + origin[i][r[i]]);

			// remove the solved task from task list
			if (origin[i][r[i]] > 0) {
				sum += origin[i][r[i]];
				totalTasksAssigned++;
				// exact match?
				if (origin[i][r[i]] == Constants.EXPERTISE_MATCH_SCORE)
					totalTasksExactMatch++;

				solvedTasks.add(r[i]);
			}
		}

		Collections.sort(solvedTasks);

		for (int i = solvedTasks.size() - 1; i >= 0; i--) {
			// remove the solved task from task list
			taskList.remove((int) solvedTasks.get(i)); // remove the last
														// element
														// first
		}

		System.out.printf("\nMaximum score: %.2f\n", sum);
		TotalScore += sum;
		TotalAssignedTasks += totalTasksAssigned;
		TotalTasksExpertiseMatch += totalTasksExactMatch;
		return sum;
	}

	/**
	 * Online matching.
	 * 
	 * @return the number of task assigned
	 */
	public double onlineMatching() {
		// replicates workers based on their maxT
		int virtualWorkerId = 0;
		HashMap<Integer, ArrayList> workerContainer = new HashMap<>();
		Iterator it0 = workerList.iterator();
		for (int i = 0; i < workerList.size(); i++) {
			ExpertWorker worker = (ExpertWorker) workerList.get(i);
			for (int j = 0; j < worker.getCapacity(); j++) {
				workerContainer.put(virtualWorkerId, containerWorker.get(i));
				virtualWorkerId++;
			}
		}

		// compute inverted container
		HashMap<Integer, ArrayList> invertedContainer = new HashMap<>();
		ArrayList<Integer> workers = new ArrayList<>();

		Iterator it1 = workerContainer.keySet().iterator();
		while (it1.hasNext()) {
			Integer workerid = (Integer) it1.next();
			ArrayList taskids = workerContainer.get(workerid);

			Iterator it2 = taskids.iterator();
			while (it2.hasNext()) {
				Integer taskid = (Integer) it2.next();
				if (!invertedContainer.containsKey(taskid)) {
					ArrayList arr = new ArrayList();
					arr.add(workerid);
					invertedContainer.put(taskid, arr);
				} else {
					invertedContainer.get(taskid).add(workerid);
				}
			}
			workers.add(workerid);
			workerid++;
		}

		// apply online bipartite matching
		OnlineBipartiteMatching obm = new OnlineBipartiteMatching(workers);

		HashMap<Integer, Integer> assignment = obm
				.onlineMatching(invertedContainer);

		// remove the assigned tasks from task list
		ArrayList<Integer> assignedTasks = new ArrayList(assignment.keySet());
		Collections.sort(assignedTasks);

		for (int i = assignedTasks.size() - 1; i >= 0; i--) {
			// remove the solved task from task list
			taskList.remove((int) assignedTasks.get(i)); // remove the last
															// element
															// first
		}

		// System.out.println(assignedTasks);

		TotalAssignedTasks += assignedTasks.size();

		System.out.println("#Assigned tasks: " + TotalAssignedTasks);

		// check correctness
		if (TotalExpiredTask + taskList.size() + TotalAssignedTasks != TaskCount) {
			System.out.println("Logic error!!!");
			System.out.println("#Expired tasks: " + TotalExpiredTask);
			System.out.println("#Remained tasks: " + taskList.size());
			System.out.println("#Assigned tasks: " + taskList.size());
			System.out.println("#Task count: " + TaskCount);
		}

		return assignedTasks.size();
	}

	/**
	 * Prints the grid.
	 */
	public void printGrid() {
		int existCnt = 0;
		int nonExistCnt = 0;
		for (int i = 0; i < rowCount; i++) {
			for (int j = 0; j < colCount; j++) {
				if (grid[i][j] != null) {
					System.out.print(grid[i][j].getDensity() + " "); // System.out.print("+ ");
					existCnt++;
				} else {
					// grid[i][j] = new Cell(-1);
					// System.out.print(grid[i][j].getDensity()+"  ");
					// System.out.print("  ");
					nonExistCnt++;
				}
			}
			System.out.println();
		}
		System.out.println("rowCount: " + rowCount + "       colcount:"
				+ colCount);
		System.out.println("existing cells count: " + existCnt
				+ "       non-existing cells:" + nonExistCnt);
	}

	/**
	 * Prints the status.
	 */
	public void printStatus() {
		System.out.println("#Tasks remained: " + taskList.size());
	}

	/**
	 * Compute input for one time instance, including container and
	 * invertedTable.
	 * 
	 * @param workerIdx
	 *            the worker idx
	 * @param mbr
	 *            the mbr
	 */
	private void rangeQuery(final int workerIdx, ExpertWorker w) {
		/* task id, increasing from 0 to the number of task - 1 */
		int t = 0;
		for (int i = 0; i < taskList.size(); i++) {
			ExpertTask task = (ExpertTask) taskList.get(i);

			/* tick expired task */
			if ((TimeInstance - task.getArrivalTime()) >= GeocrowdConstants.MAX_TASK_DURATION) {
				task.setExpired();
			} else

			/**
			 * if the task is not assigned and in the worker's working region
			 */
			if (TaskUtility.isTaskCoveredBy(task, w.getMbr())) {

				if (!taskSet.contains(t)) {
					candidateTaskIndices.add(t);
					taskSet.add(t);
				}

				if (containerPrune[workerIdx] == null)
					containerPrune[workerIdx] = new ArrayList();
				/*
				 * the container contains task index of elements in candidate
				 * tasks
				 */
				containerPrune[workerIdx].add(candidateTaskIndices.indexOf(t));

				if (!invertedContainer.containsKey(t))
					invertedContainer.put(t, new ArrayList() {
						{
							add(workerIdx);
						}
					});
				else
					invertedContainer.get(t).add(workerIdx);

			}// if not overlapped

			t++;
		}// for loop
	}

	/**
	 * Get all the task (lat/lon only) from gowalla file.
	 */
	public void readTaskLocations() {
		try {
			allTasks = new ArrayList();
			HashMap HashMap = new HashMap();
			FileReader reader = new FileReader(
					GowallaConstants.gowallaFileName_CA);
			BufferedReader in = new BufferedReader(reader);
			int cnt = 0;
			while (in.ready()) {
				String line = in.readLine();
				String[] parts = line.split("\\s");
				Integer userID = Integer.parseInt(parts[0]);
				Double lat = Double.parseDouble(parts[2]);
				Double lng = Double.parseDouble(parts[3]);
				Integer locID = Integer.parseInt(parts[4]);
				double[] location = new double[2];
				location[0] = lat;
				location[1] = lng;
				if (!HashMap.containsKey(locID)) {
					HashMap.put(locID, location);
					allTasks.add(location);
					cnt++;
				}
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
		System.out.println("No of tasks in total: " + allTasks.size());
	}

	/**
	 * Read tasks from file.
	 * 
	 * @param fileName
	 *            the file name
	 */
	@Override
	public void readTasks(String fileName) {
		TaskCount += Parser.parseExpertTasks(fileName, taskList);
	}

	/**
	 * Location of the tasks are generated randomly from user's location in
	 * gowalla.
	 * 
	 * @param fileName
	 *            the file name
	 */
	public void readTasksGowalla(String fileName) {
		int listCount = taskList.size();
		try {
			// create whole path automatically if not exist
			Path pathToFile = Paths.get(fileName);
			Files.createDirectories(pathToFile.getParent());
			
			FileWriter writer = new FileWriter(fileName);
			BufferedWriter out = new BufferedWriter(writer);
			for (int i = 0; i < allTasks.size(); i++) {
				double[] loc = allTasks.get(i);
				int time = TimeInstance;
				int taskCategory = (int) UniformGenerator.randomValue(
						new Range(0, GeocrowdConstants.TASK_CATEGORY_NUMBER),
						true);
				ExpertTask t = (ExpertTask) TaskFactory.getTask(
						TaskType.EXPERT, loc[0], loc[1]);
				t.setArrivalTime(TimeInstance);
				t.setEntropy(-1);
				t.setCategory(taskCategory);
				out.write(loc[0] + "," + loc[1] + "," + time + "," + (-1) + ","
						+ "\n");
				taskList.add(listCount, t);
				listCount++;
			}
			TaskCount += allTasks.size();
			System.out.println("#Total tasks:" + TaskCount);
			out.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * spatial tasks are randomly generated for the given spots in the area
	 * (with entropy information).
	 * 
	 * @param fileName
	 *            the file name
	 */
	public void readTasksWithEntropy(String fileName) {
		int listCount = taskList.size();
		try {
			// create whole path automatically if not exist
			Path pathToFile = Paths.get(fileName);
			Files.createDirectories(pathToFile.getParent());
			
			FileWriter writer = new FileWriter(fileName);
			BufferedWriter out = new BufferedWriter(writer);
			for (int i = 0; i < GeocrowdConstants.TASK_NUMBER; i++) {
				int randomIdx = (int) UniformGenerator.randomValue(new Range(0,
						entropyList.size()), true);
				EntropyRecord dR = entropyList.get(randomIdx);
				// generate a task inside this cell
				int row = dR.getCoord().getRowId();
				double startLat = rowToLat(row);
				double endLat = rowToLat(row + 1);
				double lat = UniformGenerator.randomValue(new Range(startLat,
						endLat), false);
				int col = dR.getCoord().getColId();
				double startLng = colToLng(col);
				double endLng = colToLng(col + 1);
				double lng = UniformGenerator.randomValue(new Range(startLng,
						endLng), false);
				int taskCategory = (int) UniformGenerator.randomValue(
						new Range(0, GeocrowdConstants.TASK_CATEGORY_NUMBER),
						true);
				ExpertTask t = (ExpertTask) TaskFactory.getTask(
						TaskType.EXPERT, lat, lng);
				t.setArrivalTime(TimeInstance);
				t.setEntropy(dR.getEntropy());
				t.setCategory(taskCategory);

				out.write(lat + ";" + lng + ";" + time + ";" + dR.getEntropy()
						+ ";" + taskCategory + "\n");
				taskList.add(listCount, t);
				listCount++;
			}
			TaskCount += GeocrowdConstants.TASK_NUMBER;
			System.out.println("#Total tasks:" + TaskCount);
			out.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * random #tasks
	 * 
	 * @param fileName
	 *            the file name
	 */
	public void readTasksWithEntropy2(int numTask, String fileName) {
		int listCount = taskList.size();
		try {
			/*
			 * calculate #tasks will be generated
			 */
			Random r = new Random();
			int step = 200;
			r.setSeed(System.nanoTime());
			// create whole path automatically if not exist
			Path pathToFile = Paths.get(fileName);
			Files.createDirectories(pathToFile.getParent());
			
			FileWriter writer = new FileWriter(fileName);
			BufferedWriter out = new BufferedWriter(writer);
			for (int i = 0; i < numTask; i++) {

				int row = 0;
				int col = 0;
				int randomIdx = (int) UniformGenerator.randomValue(new Range(0,
						entropyList.size() - 1), true);
				EntropyRecord dR = entropyList.get(randomIdx);
				row = dR.getCoord().getRowId();
				col = dR.getCoord().getColId();

				// generate a task inside this cell

				double startLat = rowToLat(row);
				double endLat = rowToLat(row + 1);
				double lat = UniformGenerator.randomValue(new Range(startLat,
						endLat), false);

				double startLng = colToLng(col);
				double endLng = colToLng(col + 1);
				double lng = UniformGenerator.randomValue(new Range(startLng,
						endLng), false);

				int time = TimeInstance;
				int taskCategory = (int) UniformGenerator.randomValue(
						new Range(0, GeocrowdConstants.TASK_CATEGORY_NUMBER),
						true);
				ExpertTask t = (ExpertTask) TaskFactory.getTask(
						TaskType.EXPERT, lat, lng);
				t.setArrivalTime(TimeInstance);
				t.setEntropy(dR.getEntropy());
				t.setCategory(taskCategory);

				out.write(lat + ";" + lng + ";" + time + ";" + dR.getEntropy()
						+ ";" + taskCategory + "\n");
				taskList.add(listCount, t);
				listCount++;
			}
			TaskCount += numTask;
			// System.out.println("#Total tasks:" + TaskCount);
			out.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	
	
	
	public void readTasksWithEntropy3(int numTask, String fileName) {
		int listCount = taskList.size();
		HashSet<Integer> generatedIndex = new HashSet<>();
		try {
			/*
			 * calculate #tasks will be generated
			 */
			Random r = new Random();
			int step = 200;
			r.setSeed(System.nanoTime());
			// create whole path automatically if not exist
			Path pathToFile = Paths.get(fileName);
			Files.createDirectories(pathToFile.getParent());
			
			FileWriter writer = new FileWriter(fileName);
			BufferedWriter out = new BufferedWriter(writer);
			int count = 0;
			for (int i = 0; i < numTask; i++) {

				//all cells are filled
				if(generatedIndex.size() == entropyList.size()){
					count+= generatedIndex.size();
					if(count >= numTask){
						break;
					}
					else if(count < numTask){
						generatedIndex.clear();
					}
				};
				int row = 0;
				int col = 0;
				int randomIdx = -1;
				while(randomIdx == -1 || generatedIndex.contains(randomIdx)){
					randomIdx = (int) UniformGenerator.randomValue(new Range(0,
						entropyList.size() - 1), true);
					
				}
				generatedIndex.add(randomIdx);
				EntropyRecord dR = entropyList.get(randomIdx);
				row = dR.getCoord().getRowId();
				col = dR.getCoord().getColId();

				// generate a task inside this cell

				double startLat = rowToLat(row);
				double endLat = rowToLat(row + 1);
				double lat = UniformGenerator.randomValue(new Range(startLat,
						endLat), false);

				double startLng = colToLng(col);
				double endLng = colToLng(col + 1);
				double lng = UniformGenerator.randomValue(new Range(startLng,
						endLng), false);

				int time = TimeInstance;
				int taskCategory = (int) UniformGenerator.randomValue(
						new Range(0, GeocrowdConstants.TASK_CATEGORY_NUMBER),
						true);
				ExpertTask t = (ExpertTask) TaskFactory.getTask(
						TaskType.EXPERT, lat, lng);
				t.setArrivalTime(TimeInstance);
				t.setEntropy(dR.getEntropy());
				t.setCategory(taskCategory);

				out.write(lat + ";" + lng + ";" + time + ";" + dR.getEntropy()
						+ ";" + taskCategory + "\n");
				taskList.add(listCount, t);
				listCount++;
			}
			TaskCount += numTask;
			// System.out.println("#Total tasks:" + TaskCount);
			out.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void readTasksFoursquare(int numTask, String fileName,
			ArrayList<Point> venues) {
		int listCount = taskList.size();
		try {
			/*
			 * calculate #tasks will be generated
			 */
			Random r = new Random();
			int step = 200;
			r.setSeed(System.nanoTime());
			// create whole path automatically if not exist
			Path pathToFile = Paths.get(fileName);
			Files.createDirectories(pathToFile.getParent());
			
			FileWriter writer = new FileWriter(fileName);
			BufferedWriter out = new BufferedWriter(writer);
			for (int i = 0; i < numTask; i++) {

				int taskid = (int) UniformGenerator.randomValue(new Range(0,
						venues.size() - 1), true);

				// generate a task inside this cell

				// double startLat = minLatitude;
				// double endLat = maxLatitude;
				// double lat = UniformGenerator.randomValue(new Range(startLat,
				// endLat), false);
				//
				// double startLng = minLongitude;
				// double endLng = maxLongitude;
				// double lng = UniformGenerator.randomValue(new Range(startLng,
				// endLng), false);

				int time = TimeInstance;
				int taskCategory = (int) UniformGenerator.randomValue(
						new Range(0, GeocrowdConstants.TASK_CATEGORY_NUMBER),
						true);
				ExpertTask t = (ExpertTask) TaskFactory.getTask(
						TaskType.EXPERT, venues.get(taskid).getX(),
						venues.get(taskid).getY());
				t.setArrivalTime(TimeInstance);
				t.setEntropy(0);
				t.setCategory(taskCategory);

				out.write(t.getLat() + "," + t.getLng() + "," + time + ","
						+ t.getEntropy() + "," + taskCategory + "\n");
				taskList.add(listCount, t);
				listCount++;
			}
			TaskCount += numTask;
			// System.out.println("#Total tasks:" + TaskCount);
			out.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	/**
	 * 
	 * @param fileName
	 *            the file name
	 */
	public static int time = 0;

	public void readTasksWithEntropy3(String fileName) {
		int listCount = taskList.size();
		try {
			/*
			 * calculate #tasks will be generated
			 */
			time++;
			int numTask = (int) (GeocrowdConstants.TASK_NUMBER + Math.pow(-1,
					time) * GeocrowdConstants.TASK_NUMBER * 2 / 3);
			// create whole path automatically if not exist
			Path pathToFile = Paths.get(fileName);
			Files.createDirectories(pathToFile.getParent());
			
			FileWriter writer = new FileWriter(fileName);
			BufferedWriter out = new BufferedWriter(writer);
			for (int i = 0; i < numTask; i++) {
				int randomIdx = (int) UniformGenerator.randomValue(new Range(0,
						entropyList.size()), true);
				EntropyRecord dR = entropyList.get(randomIdx);
				// generate a task inside this cell
				int row = dR.getCoord().getRowId();
				double startLat = rowToLat(row);
				double endLat = rowToLat(row + 1);
				double lat = UniformGenerator.randomValue(new Range(startLat,
						endLat), false);
				int col = dR.getCoord().getColId();
				double startLng = colToLng(col);
				double endLng = colToLng(col + 1);
				double lng = UniformGenerator.randomValue(new Range(startLng,
						endLng), false);
				int taskCategory = (int) UniformGenerator.randomValue(
						new Range(0, GeocrowdConstants.TASK_CATEGORY_NUMBER),
						true);
				ExpertTask t = (ExpertTask) TaskFactory
						.getTask(TaskType.EXPERT, lat, lng);
				t.setArrivalTime(TimeInstance);
				t.setEntropy(dR.getEntropy());
				t.setCategory(taskCategory);
				out.write(lat + "," + lng + "," + time + "," + dR.getEntropy()
						+ "," + taskCategory + "\n");
				taskList.add(listCount, t);
				listCount++;
			}
			TaskCount += GeocrowdConstants.TASK_NUMBER;
			System.out.println("#Total tasks:" + TaskCount);
			out.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Read workers from file Working region of each worker is computed from his
	 * past history.
	 * 
	 * @param fileName
	 *            the file name
	 */
	@Override
	public void readWorkers(String fileName) {

		/* create a new worker list at every instance */
		workerList = new ArrayList();
		WorkerCount += Parser.parseExpertWorkers(fileName, workerList);
	}

	/**
	 * Working region of each worker is computed randomly.
	 * 
	 * @param fileName
	 *            : worker file name
	 */
	public void readWorkersRandomMBR(String fileName) {
		workerList = new ArrayList();
		double maxRangeX = (maxLatitude - minLatitude)
				* GeocrowdConstants.MAX_WORKING_REGION;
		double maxRangeY = (maxLongitude - minLongitude)
				* GeocrowdConstants.MAX_WORKING_REGION;
		int cnt = 0;
		try {
			FileReader reader = new FileReader(fileName);
			BufferedReader in = new BufferedReader(reader);

			while (in.ready()) {
				String line = in.readLine();
				line = line.replace("[", "");
				line = line.replace("]", "");
				String[] parts = line.split(",");
				int maxT = (int) UniformGenerator.randomValue(new Range(0,
						GeocrowdConstants.MAX_WORKER_CAPACITY), true);
				double rangeX = UniformGenerator.randomValue(new Range(0,
						maxRangeX), false);
				double rangeY = UniformGenerator.randomValue(new Range(0,
						maxRangeY), false);
				int exp = Integer.parseInt(parts[4]);

				ExpertWorker w = (ExpertWorker) WorkerFactory
						.getWorker(WorkerType.EXPERT, Double.parseDouble(parts[2]), Double.parseDouble(parts[3]));
				w.setId(parts[0]);
				w.setCapacity(maxT);
				WorkingRegion mbr = WorkingRegion.createMBR(w.getLat(),
						w.getLng(), rangeX, rangeY);
				checkBoundaryMBR(mbr);
				w.setMbr(mbr);
				w.addExpertise(exp);
				workerList.add(w);
				cnt++;
			}

			in.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		WorkerCount += cnt;
		System.out.println("#Total workers: " + WorkerCount);
	}

	/**
	 * for a given row , converts it back to latitude.
	 * 
	 * @param row
	 *            the row
	 * @return the double
	 */
	public double rowToLat(int row) {
		return ((row) * (maxLatitude - minLatitude) / resolution) + minLatitude;
	}

	/**
	 * for a given col , converts it back to longitude.
	 * 
	 * @param col
	 *            the col
	 * @return the double
	 */
	public double colToLng(int col) {
		return ((col) * (maxLongitude - minLongitude) / resolution)
				+ minLongitude;
	}

}
