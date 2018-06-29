package ru.proshik.applepricebot.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.proshik.applepricebot.repository.model.User;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

}