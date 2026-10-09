package com.example.demo.repository;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import com.example.demo.model.Identifiable;

public abstract class InMemoryRepository<T extends Identifiable> {

    private final Map<Long, T> storage = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();

    public T save(T entity) {
        if (entity.getId() == null) {
            entity.setId(sequence.incrementAndGet());
        }
        storage.put(entity.getId(), entity);
        return entity;
    }

    public Optional<T> findById(Long id) {
        return id == null ? Optional.empty() : Optional.ofNullable(storage.get(id));
    }

    public List<T> findAll() {
        return stream().toList();
    }

    public boolean existsById(Long id) {
        return id != null && storage.containsKey(id);
    }

    public void deleteById(Long id) {
        if (id != null) {
            storage.remove(id);
        }
    }

    protected Stream<T> stream() {
        return storage.values().stream().sorted(Comparator.comparing(Identifiable::getId));
    }
}
