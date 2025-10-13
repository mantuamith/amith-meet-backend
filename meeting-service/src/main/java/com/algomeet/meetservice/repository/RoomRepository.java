package com.algomeet.meetservice.repository;

import com.algomeet.meetservice.model.Room;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomRepository extends JpaRepository<Room, String> { }
