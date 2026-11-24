package com.shema.campuspulse.data;

import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Single source of truth. Static data for now; Room + Retrofit replace it later. */
public class CampusRepository {

    private static CampusRepository instance;

    public static synchronized CampusRepository get() {
        if (instance == null) instance = new CampusRepository();
        return instance;
    }

    private final List<School> schools = new ArrayList<>();
    private final List<Team> teams = new ArrayList<>();
    private final List<Event> events = new ArrayList<>();
    private final List<Fixture> fixtures = new ArrayList<>();
    private final List<EventMedia> mediaList = new ArrayList<>();

    private final Set<String> savedEventIds = new HashSet<>();
    private final Set<String> rsvpedEventIds = new HashSet<>();
    private boolean eventsLoadFailed = false;
    private final Set<String> cheeringFixtureIds = new HashSet<>();
    private final Set<String> followedTeamIds = new HashSet<>();
    private String selectedSchoolCode = null;
    private String selectedMajor = null;

    private CampusRepository() {
        seedSchools();
        seedTeams();
        seedEvents();
        seedEventDetails();
        seedFixtures();
        seedMedia();
    }

    // ---------- Time helpers (everything is relative to the current week) ----------

    public static long weekStartMillis() {
        Calendar c = Calendar.getInstance();
        int dow = c.get(Calendar.DAY_OF_WEEK);      // Sun=1 ... Sat=7
        // rolling window: day 0 = today
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    /** day 0 = Monday ... 6 = Sunday. */
    private static long at(int day, int hour, int minute) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(weekStartMillis());
        c.add(Calendar.DAY_OF_YEAR, day);
        c.set(Calendar.HOUR_OF_DAY, hour);
        c.set(Calendar.MINUTE, minute);
        return c.getTimeInMillis();
    }

    // ---------- Seed data ----------

    private void seedSchools() {
        schools.add(new School("CSOB", "Chandaria School of Business", Arrays.asList(
                "Accounting", "Finance", "International Business Administration (IBA)",
                "Hotel and Restaurant Management (HRM)", "Global Leadership and Governance")));
        schools.add(new School("SST", "School of Science and Technology", Arrays.asList(
                "Software Engineering (SWE)", "Applied Computer Technology (APT)",
                "Information Systems & Technology (IST)", "Data Science and Analytics (DSA)")));
        schools.add(new School("SHSS", "School of Humanities and Social Sciences", Arrays.asList(
                "International Relations (IR)", "Psychology", "Criminal Justice Studies", "Sociology")));
        schools.add(new School("SPHS", "School of Pharmacy and Health Sciences", Arrays.asList(
                "Bachelor of Pharmacy (BPharm)", "BSc in Nursing (BScN)", "Applied Biochemistry",
                "Analytical Chemistry", "Epidemiology and Biostatistics")));
        schools.add(new School("SCCCA", "School of Communication, Cinematic and Creative Arts", Arrays.asList(
                "Animation", "Film Production and Directing",
                "Journalism (Broadcast, Print, Public Relations)")));
        schools.add(new School("SGSRE", "School of Graduate Studies, Research and Extension", Arrays.asList(
                "Graduate Studies (Postgraduate diplomas, Masters, PhD)")));
    }

    private void seedTeams() {
        teams.add(new Team("team_woodball", "Woodball Co-Ed", "Woodball", "Kenya Woodball Federation Circuit",
                "Coach Kamau", "Mon/Wed 4 PM, Green Lawns",
                "Co-Ed woodball team competing on the campus green lawns.", "15 active players",
                Arrays.asList("USIU 4 - 2 Daystar", "USIU 3 - 3 UON")));
        teams.add(new Team("team_tigers", "Tigers", "Basketball", "KBF League & KUSA",
                "Coach Awich", "Tue/Thu 5 PM, FBSC Gymnasium",
                "The men's basketball team in the KBF league.", "12 players",
                Arrays.asList("Tigers 78 - 65 Strathmore Blades", "Tigers 82 - 70 KPA")));
        teams.add(new Team("team_flames", "Flames", "Basketball", "KBF League & KUSA",
                "Coach George", "Mon/Wed 5 PM, FBSC Gymnasium",
                "The women's basketball team.", "12 players",
                Arrays.asList("Flames 65 - 58 Equity Hawks")));
        teams.add(new Team("team_mashujaa", "Mashujaa", "Hockey", "Kenya Hockey Union League",
                "Coach Odhiambo", "Tue/Thu 6 PM, AstroTurf",
                "The men's hockey team competing nationally.", "18 players",
                Arrays.asList("Mashujaa 2 - 1 Wazalendo")));
        teams.add(new Team("team_spartans", "Spartans", "Hockey", "Kenya Hockey Union League",
                "Coach Akinyi", "Mon/Wed 6 PM, AstroTurf",
                "The women's field hockey squad.", "16 players",
                Arrays.asList("Spartans 3 - 0 Telkom")));
        teams.add(new Team("team_martials", "Martials", "Rugby", "KRU Championship & KUSA",
                "Coach Mwangi", "Wed/Fri 4 PM, Rugby Pitch",
                "The men's rugby team.", "25 players",
                Arrays.asList("Martials 24 - 12 Catholic Monks")));
        teams.add(new Team("team_gladiators", "Gladiators", "Volleyball", "KUSA League",
                "Coach Juma", "Tue/Fri 5 PM, Indoor Arena",
                "The men's volleyball team.", "14 players",
                Arrays.asList("Gladiators 3 - 1 KU")));
        teams.add(new Team("team_fc", "USIU-Africa FC", "Soccer", "KUSA & FKF Leagues",
                "Coach Ouma", "Daily 4 PM, Soccer Field",
                "The university football club (men and women).", "22 players",
                Arrays.asList("USIU-Africa FC 2 - 1 KCA University")));
        teams.add(new Team("team_dolphins", "Dolphins", "Swimming", "National Swim Circuit",
                "Coach Maina", "Mornings and evenings, Olympic Pool",
                "The Co-Ed swimming squad at the campus Olympic pool.", "20 swimmers",
                Arrays.asList("1st place overall, KUSA Gala")));
        teams.add(new Team("team_goalball", "Goalball", "Adaptive Sports", "National Paralympic League",
                "Coach Omondi", "Sat 10 AM, Gymnasium",
                "A parasport designed for blind and visually impaired students.", "8 players",
                Arrays.asList("Goalball 5 - 3 Kenyatta University")));
        teams.add(new Team("team_chess", "Phoenix", "Mind Sports", "Kenya Chess League",
                "Coach Kim", "Thu 3 PM, Library Hub",
                "The university chess team.", "10 players",
                Arrays.asList("Phoenix 3.5 - 1.5 Strathmore")));
    }

    private void addEvent(String id, String title, String description, String school,
                          String major, String type, long start, long end, String venue,
                          int totalSeats, int seatsLeft, String organizer) {
        events.add(new Event(id, title, description, school, major, type, start, end, venue,
                totalSeats, seatsLeft, organizer));
    }

    private void seedEvents() {
        long now = System.currentTimeMillis();

        // Always "Happening Now" so the hero banner can be demoed any day
        addEvent("evt_now", "Open Mic and Coffee Hour",
                "Come relax with live music, poetry, and free coffee between classes.",
                null, null, "Social", now - 30 * 60 * 1000L, now + 90 * 60 * 1000L,
                Venues.ROOFTOP, 80, 32, "Student Council");

        addEvent("evt_1", "AI and Machine Learning Tech Talk",
                "Explore AI models, computer vision, and generative AI trends with industry leaders.",
                "SST", "Software Engineering (SWE)", "Academic", at(1, 14, 0), at(1, 16, 0),
                Venues.AUDITORIUM, 120, 14, "SST Student Association");

        addEvent("evt_2", "Chandaria Business Innovation Summit",
                "Networking and a startup pitch competition with alumni entrepreneurs and investors.",
                "CSOB", "Finance", "Career", at(1, 10, 0), at(1, 13, 0),
                Venues.AUDITORIUM, 200, 85, "CSOB Dean's Office");

        // Overlaps evt_2 on the same day, used to demo the clash warning
        addEvent("evt_3", "Android Development Workshop",
                "Hands-on session building your first Material 3 app in Java.",
                "SST", "Software Engineering (SWE)", "Academic", at(1, 11, 0), at(1, 13, 0),
                Venues.LT2, 40, 6, "SST Student Association");

        addEvent("evt_4", "Campus Woodball Championship",
                "Inter-university woodball tournament on the campus green lawns. Come support USIU!",
                null, null, "Sports", at(2, 9, 0), at(2, 15, 0),
                Venues.GREEN, 150, 120, "Sports Department");

        // Overlaps the Woodball championship
        addEvent("evt_5", "Career Fair 2026",
                "Meet employers hiring interns and graduates across every field.",
                null, null, "Career", at(2, 10, 0), at(2, 12, 0),
                Venues.FBSC, 250, 160, "Career Services");

        addEvent("evt_6", "Global Diplomacy and Peace Forum",
                "Panel on international relations, conflict resolution, and African Union policy.",
                "SHSS", "International Relations (IR)", "Academic", at(3, 13, 0), at(3, 15, 0),
                Venues.LT1, 90, 5, "IR Students Association");

        addEvent("evt_7", "Cinematic Arts Film Screening and Gala",
                "Showcase of award-winning student short films, animations, and documentaries.",
                "SCCCA", "Film Production and Directing", "Social", at(4, 18, 0), at(4, 21, 0),
                Venues.AUDITORIUM, 180, 45, "SCCCA Department");

        addEvent("evt_8", "Campus Health and Wellness Fair",
                "Free health checkups, nutrition counseling, fitness demos, and mental health workshops.",
                null, null, "Wellness", at(5, 9, 0), at(5, 14, 0),
                Venues.FBSC, 300, 210, "SPHS Student Council");

        addEvent("evt_9", "Data Science Hackathon Kickoff",
                "Form teams, meet mentors, and get the dataset for this semester's 48-hour hackathon.",
                "SST", "Data Science and Analytics (DSA)", "Academic", at(1, 10, 0), at(1, 12, 0),
                Venues.INNOVATION, 60, 18, "DSA Society");

        addEvent("evt_10", "SST Research Showcase",
                "Students and lecturers present research projects from across the School of Science and Technology.",
                "SST", null, "Academic", at(3, 14, 0), at(3, 16, 0),
                Venues.LT8, 100, 55, "SST Dean's Office");

        addEvent("evt_11", "Culinary Skills Demo",
                "Watch a live cooking demonstration, then try the techniques yourself.",
                "CSOB", "Hotel and Restaurant Management (HRM)", "Academic", at(4, 11, 0), at(4, 13, 0),
                Venues.KITCHEN, 20, 3, "HRM Students Association");

        addEvent("evt_12", "Movie Night",
                "Pick a film, grab some popcorn, and unwind with friends.",
                null, null, "Social", at(3, 19, 0), at(3, 21, 30),
                Venues.TV2, 40, 22, "Student Council");

        Collections.sort(events, new Comparator<Event>() {
            @Override
            public int compare(Event a, Event b) {
                return Long.compare(a.startMillis, b.startMillis);
            }
        });
    }
    private void enrich(String id, String details, String[] highlights, String[] agenda,
                        String audience, String bring, String cName, String cEmail, String cPhone) {
        Event e = findEvent(id);
        if (e == null) return;
        e.details = details;
        e.highlights = highlights;
        e.agenda = agenda;
        e.audience = audience;
        e.bring = bring;
        e.contactName = cName;
        e.contactEmail = cEmail;
        e.contactPhone = cPhone;
    }

    /** Sample copy and sample contacts. Real ones will come from the Django admin. */
    private void seedEventDetails() {
        enrich("evt_now", "Take a break from lectures and unwind with your peers on the SHSS Roof Top. Open Mic is a relaxed, friendly space where anyone can share music, poetry, spoken word or a short comedy bit, and everyone else is there to listen and cheer.\n\nCoffee is served throughout the session, so you can grab a cup between classes, find a seat and enjoy the view. You do not need to perform to join. Plenty of people simply come to listen.\n\nIf you would like to perform, sign up at the entrance when you arrive. Slots are around five minutes each and are given on a first come, first served basis.",
                new String[]{"Live music, poetry and spoken word from fellow students", "Free coffee between classes", "Walk-in sign-up for performers, first come first served"},
                new String[]{"0|Doors open and coffee is served", "20|Performer sign-up closes", "30|Open mic performances begin", "100|Final act and closing"},
                "Every USIU student and staff member. No registration is needed to perform.",
                "Your instrument, lyrics or poem if you plan to perform.",
                "Student Council Events Desk", "studentcouncil@usiu.ac.ke", "+254 700 000 010");

        enrich("evt_1", "This talk looks at where artificial intelligence is today and where it is heading, from machine learning models that recognise images and speech to the generative tools many students already use every day. Speakers from industry explain how these systems are built, tested and deployed in real products.\n\nExpect a practical session rather than a purely theoretical one. You will see live demonstrations, hear how teams in Nairobi and across Africa are applying machine learning to local problems, and learn what skills employers look for when they hire junior engineers.\n\nThe last part of the session is an open Q&A, so bring your questions about careers, projects and final year ideas. Seats are limited, so reserve yours early.",
                new String[]{"Live demos of computer vision and generative AI", "Real examples of AI applied to African problems", "Open Q&A on careers and final year project ideas"},
                new String[]{"0|Arrival and seating", "15|Keynote: the state of AI today", "60|Live demonstrations", "90|Open Q&A", "110|Networking and close"},
                "Built for SST students, especially Software Engineering. Anyone curious about AI is welcome.",
                "A laptop or notebook if you want to follow along, and your student ID.",
                "SST Student Association", "sst.events@usiu.ac.ke", "+254 700 000 011");

        enrich("evt_2", "The Business Innovation Summit brings together alumni entrepreneurs, investors and student founders to talk about building and funding ventures. The morning opens with short talks on how ideas become businesses, followed by a startup pitch competition where student teams present to a panel.\n\nBetween sessions you can network over refreshments, ask founders how they won their first customers and learn what investors look for in an early stage pitch. Finance students will find the funding and valuation discussions especially useful.\n\nIf you have a business idea, this is a good chance to test it with people who have done it before. Teams that want to pitch should contact the organizers ahead of time.",
                new String[]{"Startup pitch competition judged by alumni and investors", "Talks on funding, valuation and winning first customers", "Networking with founders and investors"},
                new String[]{"0|Registration and welcome", "20|Opening talks", "70|Startup pitch competition", "140|Judges feedback and awards", "165|Networking"},
                "Open to all students. Finance and business students will benefit most.",
                "Business cards if you have them, and a short pitch about your idea.",
                "CSOB Dean's Office", "csob.events@usiu.ac.ke", "+254 700 000 012");

        enrich("evt_3", "In this hands-on workshop you will build your first Android app from scratch using Java, XML layouts and Material 3. We start with the basics of an Android project, then move quickly into screens, navigation and lists so you leave with something that actually runs on your phone.\n\nThe session follows the same approach as the mobile programming course: small steps, with working code at every stage. Facilitators walk around to help when you get stuck, so beginners are very welcome.\n\nPlease install Android Studio before you come and download the starter project shared by the organizers. Seats are very limited because everyone needs a working laptop.",
                new String[]{"Build a working Material 3 app in Java", "Learn screens, navigation and RecyclerView", "Facilitators on hand to debug with you"},
                new String[]{"0|Setup check and introductions", "15|Your first screen in XML", "50|Navigation and lists", "90|Run it on a real phone", "110|Q&A and next steps"},
                "SST students, especially Software Engineering. Beginners are welcome.",
                "A laptop with Android Studio installed, a charger, and a USB cable if you want to test on your own phone.",
                "SST Student Association", "sst.events@usiu.ac.ke", "+254 700 000 011");

        enrich("evt_4", "Watch the inter-university woodball tournament unfold on the campus green lawns. Woodball is a precision sport where players use a mallet to drive a ball through a series of gates, and the lowest number of strokes wins. It is easy to follow even if you have never seen it played.\n\nThe USIU team competes against universities from across the region throughout the day. Matches run back to back, so you can drop in between classes, pick a spot on the grass and cheer for the home team.\n\nDress for the weather and bring water, since you may be outside for a while.",
                new String[]{"Inter-university matches all day on the green lawns", "Easy to follow even if you have never seen woodball", "Come and support the USIU team"},
                new String[]{"0|Teams arrive and warm up", "30|Opening matches", "180|Midday break", "210|Afternoon matches", "330|Final and awards"},
                "Everyone is welcome to watch and cheer.",
                "A hat, water and comfortable shoes for walking on the grass.",
                "Sports Department", "sports@usiu.ac.ke", "+254 700 000 013");

        enrich("evt_5", "Meet employers who are actively recruiting interns and graduates across business, technology, communication, health and more. The Career Fair is your chance to put a face to a company, ask what roles are open and learn how to stand out in their hiring process.\n\nCome prepared. Bring several copies of your CV, dress professionally and research two or three companies you are keen on before you arrive. Many recruiters collect applications on the spot and some run short interviews.\n\nCareer Services will also have a help desk for last minute CV reviews and questions about internships and industrial attachment.",
                new String[]{"Employers recruiting interns and graduates across all fields", "On the spot applications and short interviews", "CV review help desk run by Career Services"},
                new String[]{"0|Doors open", "15|Employer booths and networking", "75|CV review help desk", "105|Final call for applications"},
                "All students, especially final years and anyone looking for an internship or attachment.",
                "Printed CVs, your student ID and a notebook. Dress smart.",
                "Career Services", "careers@usiu.ac.ke", "+254 700 000 014");

        enrich("evt_6", "This forum gathers students, lecturers and guest speakers to discuss how diplomacy shapes peace and security in Africa and beyond. The panel looks at conflict resolution, the role of the African Union and what young people can contribute to peacebuilding.\n\nEach panelist shares a short opening view, then the discussion opens to the floor. Expect lively, respectful debate and plenty of room for questions from the audience.\n\nInternational Relations students are encouraged to come with a question or a case study they have been working on in class. Seats are limited, so reserve early.",
                new String[]{"Panel on conflict resolution and African Union policy", "Open floor for audience questions", "Insights from practitioners in diplomacy"},
                new String[]{"0|Welcome and introductions", "15|Panel opening statements", "50|Moderated discussion", "85|Audience questions", "110|Closing remarks"},
                "Open to all, especially International Relations and Sociology students.",
                "A question or case study to share, and a notebook.",
                "IR Students Association", "shss.events@usiu.ac.ke", "+254 700 000 015");

        enrich("evt_7", "An evening celebrating the best student short films, animations and documentaries from the School of Communication, Cinematic and Creative Arts. The screening showcases a year of creative work, from first-time directors to final year projects.\n\nAfter the films, the filmmakers take the stage for a short conversation about their process, the challenges of making work on a student budget and what inspired their stories. Awards are presented at the end of the gala.\n\nBring friends, dress up if you like, and come ready to be moved, amused and inspired.",
                new String[]{"Award-winning student films, animations and documentaries", "Conversations with the filmmakers", "Gala awards ceremony"},
                new String[]{"0|Doors open", "20|Screening begins", "110|Filmmaker conversation", "150|Awards", "170|Closing"},
                "Open to everyone. Students from all schools are welcome.",
                "Friends and an open mind. Dress up if you like.",
                "SCCCA Department", "sccca.events@usiu.ac.ke", "+254 700 000 016");

        enrich("evt_8", "A full morning dedicated to looking after your body and mind. The Health and Wellness Fair offers free health checkups, nutrition counselling, fitness demonstrations and workshops on mental health and stress during exams.\n\nStudents from the School of Pharmacy and Health Sciences help run many of the stations, so you can ask questions about nutrition, sleep and managing stress in a friendly setting.\n\nWalk in at any time. Most stations take about ten minutes, so you can visit several.",
                new String[]{"Free health checkups and nutrition counselling", "Fitness demonstrations you can join", "Mental health and stress management workshops"},
                new String[]{"0|Stations open", "60|Fitness demonstration", "120|Mental health workshop", "210|Nutrition talk", "285|Closing"},
                "All students and staff.",
                "Water, comfortable clothes for the fitness demo and your student ID.",
                "SPHS Student Council", "sphs.events@usiu.ac.ke", "+254 700 000 017");

        enrich("evt_9", "This is the official start of the semester's 48-hour data science hackathon. At the kickoff you will meet mentors, hear the rules and judging criteria, and receive the dataset your team will work with.\n\nIf you do not have a team yet, there is a team-forming session right at the start, so come on your own and find collaborators with complementary skills in analysis, modelling, visualisation and storytelling.\n\nMentors from industry and the faculty will be around to answer questions about the challenge, tools and deliverables. Make sure you have a laptop with Python or R ready.",
                new String[]{"Meet mentors and teammates", "Dataset and challenge revealed", "Rules and judging criteria explained"},
                new String[]{"0|Welcome and rules", "20|Team forming", "45|Dataset and challenge revealed", "80|Mentor Q&A", "110|Wrap up and next steps"},
                "Data Science and Analytics students first, but any SST student can join a team.",
                "A laptop with Python or R set up, a charger and your project ideas.",
                "DSA Society", "dsa.society@usiu.ac.ke", "+254 700 000 018");

        enrich("evt_10", "The Research Showcase is where students and lecturers from across the School of Science and Technology present their work. Projects range from machine learning and cybersecurity to software systems and applied computing.\n\nEach presenter has a few minutes to explain the problem, the approach and the results, followed by questions from the audience. It is an excellent way to discover research topics, find supervisors and see what is possible with a final year project.\n\nStudents considering postgraduate study are especially encouraged to attend and speak to the researchers afterwards.",
                new String[]{"Short talks on current SST research", "Meet potential project supervisors", "Ideas for your final year project"},
                new String[]{"0|Welcome", "10|Student presentations", "70|Faculty research talks", "100|Questions and discussion", "115|Closing"},
                "All SST students. Final year students and those considering postgraduate study will gain the most.",
                "A notebook and your questions.",
                "SST Dean's Office", "sst.events@usiu.ac.ke", "+254 700 000 019");

        enrich("evt_11", "Watch a chef instructor prepare a dish from start to finish in the Freida Brown Kitchen Lab, explaining each technique as they go, from knife skills to plating. Then it is your turn: small groups try the techniques at their own stations.\n\nThe session is designed for Hotel and Restaurant Management students but is open to anyone curious about professional cooking. Because the lab has limited stations, seats are very limited and fill up fast.\n\nWear closed shoes and tie back long hair for safety.",
                new String[]{"Live cooking demonstration from start to plating", "Hands-on practice at your own station", "Learn professional kitchen techniques"},
                new String[]{"0|Safety briefing", "10|Live demonstration", "60|Hands-on practice", "105|Tasting and feedback", "115|Clean up"},
                "HRM students first. Open to other students if seats remain.",
                "Closed shoes, and tie back long hair.",
                "HRM Students Association", "hrm.events@usiu.ac.ke", "+254 700 000 020");

        enrich("evt_12", "Switch off from assignments and watch a film with friends in Freida Brown TV Room 2. Movie Night is a relaxed, social evening where the audience helps choose what to watch.\n\nThe title is announced by the Student Council in the days before the event. It is a great way to meet people from other schools. Seats are limited to the size of the room, so reserve a spot and arrive a few minutes early to get a good view.",
                new String[]{"Relaxed social evening with friends", "Film chosen by students", "Meet people from other schools"},
                new String[]{"0|Doors open and seating", "15|Film begins", "140|Wrap up"},
                "Every USIU student.",
                "Your student ID and a friend.",
                "Student Council Events Desk", "studentcouncil@usiu.ac.ke", "+254 700 000 010");
    }
    private void seedFixtures() {
        fixtures.add(new Fixture("fix_1", "team_woodball", "Woodball Co-Ed", "Woodball", "JKUAT Woodball Club",
                at(2, 14, 0), at(2, 16, 0), "Campus Green Lawns", true, "Kenya Woodball Federation", 142));
        fixtures.add(new Fixture("fix_2", "team_fc", "USIU-Africa FC", "Soccer", "KCA University",
                at(3, 15, 30), at(3, 17, 30), "Soccer Field", true, "KUSA League", 203));
        fixtures.add(new Fixture("fix_3", "team_tigers", "Tigers", "Basketball", "Strathmore Blades",
                at(4, 16, 30), at(4, 18, 30), "FBSC Gymnasium", true, "KBF Premier League", 280));
        fixtures.add(new Fixture("fix_4", "team_goalball", "Goalball", "Adaptive Sports", "Kenyatta University",
                at(5, 10, 0), at(5, 12, 0), "FBSC Gymnasium", true, "National Paralympic League", 64));
        fixtures.add(new Fixture("fix_5", "team_flames", "Flames", "Basketball", "Equity Hawks",
                at(5, 14, 0), at(5, 16, 0), "Nyayo Gymnasium", false, "KBF League", 95));
        fixtures.add(new Fixture("fix_6", "team_mashujaa", "Mashujaa", "Hockey", "Police HC",
                at(6, 15, 0), at(6, 17, 0), "City Park AstroTurf", false, "KHU League", 110));
    }

    private void seedMedia() {
        long now = System.currentTimeMillis();
        mediaList.add(new EventMedia("med_1", "evt_now", "Great coffee vibes and acoustic session!", "Photo", "res/drawable/ic_event.xml", "Student Council", now - 3600000L));
        mediaList.add(new EventMedia("med_2", "evt_1", "Packed auditorium during the AI keynote", "Photo", "res/drawable/ic_event.xml", "SST Association", now - 86400000L));
        mediaList.add(new EventMedia("med_3", "evt_4", "Winning goal celebration on the green lawns!", "Video", "res/drawable/ic_sports.xml", "Sports Dept", now - 172800000L));
    }

    public List<EventMedia> getMediaForEvent(String eventId) {
        List<EventMedia> out = new ArrayList<>();
        for (EventMedia m : mediaList) {
            if (m.eventId.equals(eventId)) out.add(m);
        }
        return out;
    }

    public void addEventMedia(EventMedia media) {
        mediaList.add(0, media);
    }

    // ---------- Read ----------

    public List<School> getSchools() { return schools; }
    public List<Team> getTeams() { return teams; }
    public List<Event> getEvents() { return events; }
    public boolean hasEventLoadFailed() { return eventsLoadFailed; }
    public List<Fixture> getFixtures() { return fixtures; }

    public School findSchool(String code) {
        for (School s : schools) if (s.code.equals(code)) return s;
        return null;
    }

    public Event findEvent(String id) {
        for (Event e : events) if (e.id.equals(id)) return e;
        return null;
    }

    public Team findTeam(String id) {
        for (Team t : teams) if (t.id.equals(id)) return t;
        return null;
    }

    public Fixture findFixture(String id) {
        for (Fixture f : fixtures) if (f.id.equals(id)) return f;
        return null;
    }

    /** day 0 = Monday ... 6 = Sunday of the current week. */
    public List<Event> getEventsForDay(int day) {
        long from = at(day, 0, 0);
        long to = from + 24L * 60 * 60 * 1000;
        List<Event> out = new ArrayList<>();
        for (Event e : events) {
            if (e.startMillis >= from && e.startMillis < to) out.add(e);
        }
        return out;
    }

    /** FR-02 / BR-01: matching school and major, plus campus-wide events. */
    public List<Event> getForYou() {
        List<Event> out = new ArrayList<>();
        for (Event e : events) {
            if (selectedSchoolCode == null || e.schoolCode == null) {
                out.add(e);
            } else if (e.schoolCode.equals(selectedSchoolCode)
                    && (e.major == null || selectedMajor == null || e.major.equals(selectedMajor))) {
                out.add(e);
            }
        }
        return out;
    }

    public Event getHappeningNow() {
        long now = System.currentTimeMillis();
        for (Event e : events) {
            if (e.startMillis <= now && now <= e.endMillis) return e;
        }
        return null;
    }

    public List<Fixture> getFixturesForFollowedTeams() {
        List<Fixture> out = new ArrayList<>();
        for (Fixture f : fixtures) {
            if (followedTeamIds.contains(f.teamId)) out.add(f);
        }
        return out;
    }

    // ---------- User state (in memory for now) ----------

    public void clearUserState() {
        for (String fixtureId : cheeringFixtureIds) {
            Fixture f = findFixture(fixtureId);
            if (f != null) {
                f.fanCount = Math.max(0, f.fanCount - 1);
            }
        }
        savedEventIds.clear();
        rsvpedEventIds.clear();
        cheeringFixtureIds.clear();
        followedTeamIds.clear();

        selectedSchoolCode = null;
        selectedMajor = null;

        for (Event event : events) {
            event.isSaved = false;
            event.isRsvped = false;
        }

        eventsLoadFailed = false;
    }

    public String getSelectedSchoolCode() { return selectedSchoolCode; }
    public String getSelectedMajor() { return selectedMajor; }

    public void setSelection(String schoolCode, String major) {
        this.selectedSchoolCode = schoolCode;
        this.selectedMajor = major;
    }

    public boolean isSaved(String eventId) { return savedEventIds.contains(eventId); }

    public void toggleSaved(String eventId) {
        if (!savedEventIds.remove(eventId)) savedEventIds.add(eventId);
    }

    public boolean hasRsvped(String eventId) {
        Event e = findEvent(eventId);
        if (e != null && e.isRsvped) return true;
        return rsvpedEventIds.contains(eventId);
    }

    /** BR-02 / BR-03: one RSVP per device per event; closed when full. */
    public boolean rsvp(String eventId) {
        Event e = findEvent(eventId);
        if (e == null || e.isFull() || rsvpedEventIds.contains(eventId)) return false;
        if (e.seatsLeft != null) e.seatsLeft--;
        rsvpedEventIds.add(eventId);
        return true;
    }

    public void rsvpApi(android.content.Context context, String eventId, String email, String studentId, String notes,
                        Runnable onSuccess, java.util.function.Consumer<String> onError) {
        Event e = findEvent(eventId);
        if (e == null || e.isFull()) {
            if (onError != null) onError.accept("Event is full or not found");
            return;
        }

        JsonObject body = new JsonObject();
        if (email != null) body.addProperty("email", email);
        if (studentId != null) body.addProperty("student_id", studentId);
        if (notes != null) body.addProperty("notes", notes);

        ApiClient.get(context).rsvpEvent(eventId, body).enqueue(new retrofit2.Callback<JsonObject>() {
            @Override
            public void onResponse(retrofit2.Call<JsonObject> call, retrofit2.Response<JsonObject> response) {
                if (response.isSuccessful() && response.body() != null) {
                    JsonObject obj = response.body();
                    if (obj.has("seats_left") && !obj.get("seats_left").isJsonNull()) {
                        e.seatsLeft = obj.get("seats_left").getAsInt();
                    }
                    e.isRsvped = true;
                    rsvpedEventIds.add(eventId);
                    if (onSuccess != null) onSuccess.run();
                } else {
                    String err = "RSVP failed (Time conflict or full)";
                    try {
                        if (response.errorBody() != null) {
                            String errJson = response.errorBody().string();
                            JsonObject obj = new com.google.gson.JsonParser().parse(errJson).getAsJsonObject();
                            if (obj.has("error")) err = obj.get("error").getAsString();
                        }
                    } catch (Exception ignored) {}
                    if (onError != null) onError.accept(err);
                }
            }

            @Override
            public void onFailure(retrofit2.Call<JsonObject> call, Throwable t) {
                if (onError != null) onError.accept("Network error. Your registration could not be confirmed. Please try again.");
            }
        });
    }

    public List<Event> getRecommendedEvents(String currentEventId) {
        long now = System.currentTimeMillis();
        List<Event> recs = new ArrayList<>();
        for (Event e : events) {
            if (!e.id.equals(currentEventId) && e.endMillis > now && !e.isFull()) {
                recs.add(e);
            }
        }
        return recs;
    }

    public void cancelRsvpApi(android.content.Context context, String eventId,
                              Runnable onSuccess, java.util.function.Consumer<String> onError) {
        Event e = findEvent(eventId);
        if (e == null) {
            if (onError != null) onError.accept("Event not found");
            return;
        }

        ApiClient.get(context).cancelRsvp(eventId).enqueue(new retrofit2.Callback<JsonObject>() {
            @Override
            public void onResponse(retrofit2.Call<JsonObject> call, retrofit2.Response<JsonObject> response) {
                if (response.isSuccessful()) {
                    if (response.body() != null) {
                        JsonObject obj = response.body();
                        if (obj.has("seats_left") && !obj.get("seats_left").isJsonNull()) {
                            e.seatsLeft = obj.get("seats_left").getAsInt();
                        }
                    }
                    e.isRsvped = false;
                    rsvpedEventIds.remove(eventId);
                    if (onSuccess != null) onSuccess.run();
                } else {
                    String err = "Cancellation failed";
                    try {
                        if (response.errorBody() != null) {
                            String errJson = response.errorBody().string();
                            JsonObject obj = new com.google.gson.JsonParser().parse(errJson).getAsJsonObject();
                            if (obj.has("error")) err = obj.get("error").getAsString();
                        }
                    } catch (Exception ignored) {}
                    if (onError != null) onError.accept(err);
                }
            }

            @Override
            public void onFailure(retrofit2.Call<JsonObject> call, Throwable t) {
                if (onError != null) onError.accept("Network error. Your registration could not be cancelled. Please try again.");
            }
        });
    }

    public void toggleSavedApi(android.content.Context context, String eventId, Runnable onChanged, java.util.function.Consumer<String> onError) {
        boolean isCurrentlySaved = savedEventIds.contains(eventId);
        retrofit2.Call<JsonObject> call;
        Event event = findEvent(eventId);

        if (isCurrentlySaved) {
            savedEventIds.remove(eventId);
            if (event != null) event.isSaved = false;
            call = ApiClient.get(context).unbookmarkEvent(eventId);
        } else {
            savedEventIds.add(eventId);
            if (event != null) event.isSaved = true;
            call = ApiClient.get(context).bookmarkEvent(eventId);
        }

        call.enqueue(new retrofit2.Callback<JsonObject>() {
            @Override
            public void onResponse(retrofit2.Call<JsonObject> call, retrofit2.Response<JsonObject> response) {
                if (!response.isSuccessful()) {
                    if (isCurrentlySaved) savedEventIds.add(eventId);
                    else savedEventIds.remove(eventId);
                    if (event != null) event.isSaved = isCurrentlySaved;
                    if (onError != null) onError.accept("Failed to update bookmark");
                } else {
                    if (onChanged != null) onChanged.run();
                }
            }

            @Override
            public void onFailure(retrofit2.Call<JsonObject> call, Throwable t) {
                if (isCurrentlySaved) savedEventIds.add(eventId);
                else savedEventIds.remove(eventId);
                if (event != null) event.isSaved = isCurrentlySaved;
                if (onError != null) onError.accept("Network error. Please check your connection.");
            }
        });
    }

    public Set<String> getFollowedTeamIds() { return new HashSet<>(followedTeamIds); }

    public boolean isFollowing(String teamId) { return followedTeamIds.contains(teamId); }

    public void toggleFollow(String teamId) {
        if (!followedTeamIds.remove(teamId)) followedTeamIds.add(teamId);
    }

    public boolean isCheering(String fixtureId) { return cheeringFixtureIds.contains(fixtureId); }

    public void fetchEventsFromApi(android.content.Context context, Runnable onComplete) {
        eventsLoadFailed = false;

        ApiClient.get(context).getEvents().enqueue(new retrofit2.Callback<List<Event>>() {
            @Override
            public void onResponse(retrofit2.Call<List<Event>> call, retrofit2.Response<List<Event>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    events.clear();
                    events.addAll(response.body());
                    rsvpedEventIds.clear();
                    savedEventIds.clear();

                    for (Event e : events) {
                        if (e.isRsvped) rsvpedEventIds.add(e.id);
                        if (e.isSaved) savedEventIds.add(e.id);
                    }
                    eventsLoadFailed = false;
                } else {
                    eventsLoadFailed = events.isEmpty();
                }

                if (onComplete != null) onComplete.run();
            }

            @Override
            public void onFailure(retrofit2.Call<List<Event>> call, Throwable t) {
                eventsLoadFailed = events.isEmpty();
                if (onComplete != null) onComplete.run();
            }
        });
    }

    private boolean fixturesLoadFailed = false;

    public boolean hasFixtureLoadFailed() {
        return fixturesLoadFailed;
    }

    public void fetchFixturesFromApi(android.content.Context context, Runnable onComplete) {
        fixtures.clear();
        fixturesLoadFailed = false;

        ApiClient.get(context).getFixtures().enqueue(new retrofit2.Callback<List<Fixture>>() {
            @Override
            public void onResponse(retrofit2.Call<List<Fixture>> call, retrofit2.Response<List<Fixture>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    fixtures.clear();
                    fixtures.addAll(response.body());
                    fixturesLoadFailed = false;
                } else {
                    fixtures.clear();
                    fixturesLoadFailed = true;
                }
                if (onComplete != null) onComplete.run();
            }

            @Override
            public void onFailure(retrofit2.Call<List<Fixture>> call, Throwable t) {
                fixtures.clear();
                fixturesLoadFailed = true;
                if (onComplete != null) onComplete.run();
            }
        });
    }

    /** BR-04: cheering adds the match to My Week and bumps the public fan counter. */
    public void toggleCheer(String fixtureId) {
        Fixture f = findFixture(fixtureId);
        if (f == null) return;
        if (cheeringFixtureIds.remove(fixtureId)) {
            f.fanCount = Math.max(0, f.fanCount - 1);
        } else {
            cheeringFixtureIds.add(fixtureId);
            f.fanCount++;
        }
    }

    public void fetchNotificationsApi(android.content.Context context, androidx.core.util.Consumer<List<NotificationItem>> onSuccess, androidx.core.util.Consumer<String> onError) {
        ApiClient.get(context).getNotifications().enqueue(new retrofit2.Callback<List<NotificationItem>>() {
            @Override
            public void onResponse(retrofit2.Call<List<NotificationItem>> call, retrofit2.Response<List<NotificationItem>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    if (onSuccess != null) onSuccess.accept(response.body());
                } else {
                    if (onError != null) onError.accept("Failed to load notifications (" + response.code() + ")");
                }
            }

            @Override
            public void onFailure(retrofit2.Call<List<NotificationItem>> call, Throwable t) {
                if (onError != null) onError.accept(t.getMessage());
            }
        });
    }

    public void markNotificationReadApi(android.content.Context context, int notifId, Runnable onComplete) {
        ApiClient.get(context).markNotificationRead(notifId).enqueue(new retrofit2.Callback<com.google.gson.JsonObject>() {
            @Override
            public void onResponse(retrofit2.Call<com.google.gson.JsonObject> call, retrofit2.Response<com.google.gson.JsonObject> response) {
                if (onComplete != null) onComplete.run();
            }

            @Override
            public void onFailure(retrofit2.Call<com.google.gson.JsonObject> call, Throwable t) {
                if (onComplete != null) onComplete.run();
            }
        });
    }

    public void dismissNotificationApi(android.content.Context context, int notifId, Runnable onComplete) {
        ApiClient.get(context).dismissNotification(notifId).enqueue(new retrofit2.Callback<com.google.gson.JsonObject>() {
            @Override
            public void onResponse(retrofit2.Call<com.google.gson.JsonObject> call, retrofit2.Response<com.google.gson.JsonObject> response) {
                if (onComplete != null) onComplete.run();
            }

            @Override
            public void onFailure(retrofit2.Call<com.google.gson.JsonObject> call, Throwable t) {
                if (onComplete != null) onComplete.run();
            }
        });
    }

    public void markAllNotificationsReadApi(android.content.Context context, Runnable onComplete) {
        ApiClient.get(context).markAllNotificationsRead().enqueue(new retrofit2.Callback<com.google.gson.JsonObject>() {
            @Override
            public void onResponse(retrofit2.Call<com.google.gson.JsonObject> call, retrofit2.Response<com.google.gson.JsonObject> response) {
                if (onComplete != null) onComplete.run();
            }

            @Override
            public void onFailure(retrofit2.Call<com.google.gson.JsonObject> call, Throwable t) {
                if (onComplete != null) onComplete.run();
            }
        });
    }
}
