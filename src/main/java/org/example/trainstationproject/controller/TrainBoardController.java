package org.example.trainstationproject.controller;

import org.example.trainstationproject.model.Train;
import org.example.trainstationproject.service.UzParserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.PostConstruct;
import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Controller
public class TrainBoardController {
    private static final String FILE_NAME = "trains.txt";
    private List<Train> trains = new ArrayList<>();

    @Autowired
    private UzParserService uzParserService;

    @PostConstruct
    public void init() {
        loadFromFile();
    }

    @GetMapping("/")
    public String index(Model model, @RequestParam(required = false) String search) {
        List<Train> displayList = new ArrayList<>(trains);
        if (search != null && !search.isEmpty()) {
            displayList.removeIf(t -> !t.getDestination().toLowerCase().contains(search.toLowerCase()));
        }
        displayList.sort((t1, t2) -> {
            if (t1.isActual() && !t2.isActual()) return -1;
            if (!t1.isActual() && t2.isActual()) return 1;
            return t1.getActualDepartureTime().compareTo(t2.getActualDepartureTime());
        });
        model.addAttribute("trains", displayList);
        model.addAttribute("actualCount", trains.stream().filter(Train::isActual).count());
        model.addAttribute("totalCount", trains.size());
        return "index";
    }

    @PostMapping("/sync-uz")
    public String syncWithUz() {
        List<Train> realTrains = uzParserService.parseLvivStation();
        if (!realTrains.isEmpty()) {
            this.trains = realTrains;
            saveToFile();
        }
        return "redirect:/";
    }

    @Scheduled(fixedRate = 60000)
    public void autoUpdateTrains() {
        LocalDateTime now = LocalDateTime.now();
        Random r = new Random();
        boolean changed = false;
        for (Train t : trains) {
            long minsToActualDeparture = ChronoUnit.MINUTES.between(now, t.getActualDepartureTime());
            if (minsToActualDeparture > 0 && minsToActualDeparture <= 5 && t.getDelayMinutes() < 30) {
                if (r.nextInt(100) < 25) {
                    t.setDelayMinutes(t.getDelayMinutes() + 5);
                    changed = true;
                }
            }
        }
        if (changed) saveToFile();
    }

    @PostMapping("/add")
    public String add(@RequestParam String dest, @RequestParam String time, @RequestParam String type) {
        try {
            LocalDateTime ldt = LocalDateTime.parse(time);
            Random r = new Random();
            Train t = new Train(String.format("%03d%c", r.nextInt(900) + 10, (char) ('А' + r.nextInt(26))),
                    dest, ldt, (r.nextInt(12) + 1) + " колія", type, 0);
            trains.add(t);
            saveToFile();
        } catch (Exception e) { e.printStackTrace(); }
        return "redirect:/";
    }

    @PostMapping("/delete/{num}")
    public String delete(@PathVariable String num) {
        trains.removeIf(t -> t.getNumber().equals(num));
        saveToFile();
        return "redirect:/";
    }

    private void saveToFile() {
        try (PrintWriter w = new PrintWriter(new FileWriter(FILE_NAME))) {
            for (Train t : trains) {
                w.println(t.getNumber() + "|" + t.getDestination() + "|" +
                        t.getDepartureDateTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + "|" +
                        t.getPlatform() + "|" + t.getType() + "|" + t.getDelayMinutes());
            }
        } catch (IOException e) { e.printStackTrace(); }
    }

    private void loadFromFile() {
        File f = new File(FILE_NAME);
        if (!f.exists()) return;
        try (Scanner s = new Scanner(f)) {
            while (s.hasNextLine()) {
                String[] p = s.nextLine().split("\\|");
                if (p.length == 6) {
                    trains.add(new Train(p[0], p[1], LocalDateTime.parse(p[2]), p[3], p[4], Integer.parseInt(p[5])));
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
    }
}