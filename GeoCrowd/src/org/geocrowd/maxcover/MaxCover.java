/**
 * *****************************************************************************
 * @ Year 2013 This is the source code of the following papers.
 *
 * 1) Geocrowd: A Server-Assigned Crowdsourcing Framework. Hien To, Leyla
 * Kazemi, Cyrus Shahabi.
 *
 *
 * Please contact the author Hien To, ubriela@gmail.com if you have any
 * question.
 *
 * Contributors: Hien To - initial implementation
 ******************************************************************************
 */
package org.geocrowd.maxcover;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;


/**
 * The Class MaxSetCover.
 */
public abstract class MaxCover {

	/**
	 * Each element in the list is associated with a worker, and each worker
	 * contains a set of task ids that he is eligible to perform
	 *
	 */
	public HashMap<Integer, HashMap<Integer, Integer>> mapSets = null;

	public int budget = 0; // budget

	public double gain = 0.0; // this gain is updated at every stage of greedy
							// algorithm
	
	public MaxCover() {
		
	}

	/**
	 * All the task index in the candidate tasks (not the task list).
	 */
	public HashSet<Integer> universe = null;
	public HashSet<Integer> assignedTaskSet = new HashSet<Integer>();

	public double averageDelayTime = 0;
	/**
	 * The number of assigned tasks.
	 */
	public int assignedTasks = 0;
	public double assignedUtility = 0.0;

	/**
	 * The assigned workers
	 */
	public HashSet<Integer> assignWorkers = new HashSet<>();
	/**
	 * The current time instance.
	 */
	Integer currentTimeInstance = 0;

	public MaxCover(ArrayList container, Integer currentTI) {
		mapSets = new HashMap<>();
		int k = 0;
		universe = new HashSet<>();
		currentTimeInstance = currentTI;
		
		for(int i =0; i < container.size(); i++){
			mapSets.put(i, (HashMap<Integer, Integer>) container.get(i));
			universe.addAll(((HashMap<Integer, Integer>) container.get(i)).keySet());
			
		}
		/**
		if (container.size() > 0
				&& container.get(0).getClass().isInstance(new ArrayList())) {
			// !!! this part may not in use
			System.out.println("debug: without deadline info");
			;

		} else {
			/**
			 * In case each task has a deadline
			 */ /**
			for (int i = 0; i < container.size(); i++) {
				HashMap<Integer, Integer> items = (HashMap<Integer, Integer>) container
						.get(i);
				if (items != null) {
					
					HashMap<Integer, Integer> itemSet = new HashMap<>(items);
					mapSets.put(k++, itemSet);
					universe.addAll(itemSet.keySet());
				} else {
					System.out.println("NULL");
				}
			}
		}
		**/
	}

	/**
	 * Max set cover.
	 *
	 * Note that all the tasks will be assigned after this.
	 *
	 * @return the number of assigned workers
	 */
	public abstract HashSet<Integer> maxCover();
}