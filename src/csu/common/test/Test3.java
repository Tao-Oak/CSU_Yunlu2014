package csu.common.test;

import java.util.PriorityQueue;
import java.util.Random;

public class Test3 {

	public static void main(String[] args) {
		Random random = new Random();
		PriorityQueue<Integer> queue = new PriorityQueue<>();
		for (int i = 0; i < 10; i++) {
			Integer integer = new Integer(random.nextInt(100));
			queue.add(integer);
		}
		System.out.println(queue);
	}
}
