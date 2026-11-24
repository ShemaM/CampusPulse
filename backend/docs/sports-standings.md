# Sports standings rules

Standings include fixtures only when both scores exist and the scheduled end
time has passed in the configured timezone. Football uses win = 3, draw = 1,
loss = 0, then goal difference, goals for, and team name as tie breakers.

The fixture dataset does not contain competition rules for Basketball,
Volleyball, Rugby, or Netball. Those sports therefore use an explicitly
illustrative win = 3, draw = 1, loss = 0 calculation, followed by score
difference, points for, and team name. API responses label these tables
`DEMONSTRATION ONLY`; they are not official competition standings. Volleyball
scores are treated as the stored set totals, and other sports use their stored
score values without inferring additional rules.

Teams without an existing campus `team_id` receive a stable catalog identifier
derived from sport and opponent name. Existing fixture identifiers and rows are
not rewritten to introduce the catalog.
