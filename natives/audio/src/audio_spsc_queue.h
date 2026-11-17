#pragma once

#include <atomic>
#include <cstddef>
#include <vector>

namespace verktex::audio {

// A bounded queue with one producer and one consumer, neither of which ever blocks or allocates once
// it's constructed, so the output's callback can be either side. Several producers must take a lock of
// their own around push.
template <typename T>
class SpscQueue {
public:
    explicit SpscQueue(size_t capacity) : slots(capacity + 1) {}

    // Producer side. False when the queue is full.
    bool push(const T &value) {
        size_t tail = writeIndex.load(std::memory_order_relaxed);
        size_t next = (tail + 1) % slots.size();
        if (next == readIndex.load(std::memory_order_acquire)) return false;

        slots[tail] = value;
        writeIndex.store(next, std::memory_order_release);
        return true;
    }

    // Consumer side. False when the queue is empty.
    bool pop(T &value) {
        size_t head = readIndex.load(std::memory_order_relaxed);
        if (head == writeIndex.load(std::memory_order_acquire)) return false;

        value = slots[head];
        readIndex.store((head + 1) % slots.size(), std::memory_order_release);
        return true;
    }

private:
    std::vector<T> slots;
    std::atomic<size_t> writeIndex{0};
    std::atomic<size_t> readIndex{0};
};

}
