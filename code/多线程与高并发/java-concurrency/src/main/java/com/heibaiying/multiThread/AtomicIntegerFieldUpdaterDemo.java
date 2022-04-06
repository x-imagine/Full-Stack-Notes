package com.heibaiying.multiThread;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

public class AtomicIntegerFieldUpdaterDemo {

    static class Task implements Runnable {
        private Candidate candidate;
        private CountDownLatch latch;
        private AtomicIntegerFieldUpdater fieldUpdater;

        public Task(Candidate candidate, CountDownLatch latch, AtomicIntegerFieldUpdater fieldUpdater) {
            this.candidate = candidate;
            this.latch = latch;
            this.fieldUpdater = fieldUpdater;
        }

        @Override
        public void run() {
            fieldUpdater.incrementAndGet(candidate);
            latch.countDown();
        }
    }

    @Data
    @AllArgsConstructor
    private static class Candidate {
        private String name;
        public volatile int score;
    }

    public static void main(String[] args) throws InterruptedException {
        int number = 100000;
        CountDownLatch countDownLatch = new CountDownLatch(number);
        ExecutorService executorService = Executors.newFixedThreadPool(10);
        Candidate tommy = new Candidate("tommy", 0);
        AtomicIntegerFieldUpdater<Candidate> fieldUpdater = AtomicIntegerFieldUpdater.newUpdater(Candidate.class, "score");
        for (int i = 0; i < number; i++) {
            executorService.execute(new Task(tommy, countDownLatch, fieldUpdater));
        }
        countDownLatch.await();
        System.out.println(tommy.getName() + "获得票数:" + tommy.getScore());
        executorService.shutdown();
    }
}
