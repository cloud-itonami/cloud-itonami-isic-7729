(ns householdrentalops.facts
  "Reference facts for community household-goods rent-to-own operations
  coordination: a household-goods category vocabulary and a robotics
  mission-leg vocabulary. This namespace contains pure lookup functions
  for domain reference data -- the Governor and Advisor consult these
  instead of inventing thresholds. Mirrors `tobaccoops.facts`
  (cloud-itonami-isic-0115) / `vehiclerentalops.facts`-equivalent
  reference tables in shape, adapted to ISIC 7729: renting and leasing of
  other personal and household goods (furniture, appliances and consumer
  electronics rented to individual consumers, frequently under
  rent-to-own arrangements -- distinct from motor vehicles (7710),
  recreational/sports goods (7721) and construction tools
  (unspsc-27), per this repo's README scope note).")

(def goods-categories
  "Reference household-goods categories this actor's rental-purchase
  agreements commonly cover (README: 'furniture, appliances and
  consumer electronics rented to individual consumers'). Informational
  only -- NOT a validated enum; the advisor/operator may propose other
  category strings and the Governor does not reject unlisted values
  here (mirrors `tobaccoops.facts/field-operation-types`'s
  informational-only discipline)."
  #{"furniture" "major-appliance" "small-appliance" "consumer-electronics"})

(defn goods-category-known?
  [category]
  (contains? goods-categories category))

(def mission-types
  "Reference robotics mission legs this actor's dispatch proposals
  cover (README: 'robotics-assisted delivery/condition inspection and
  pickup'). Informational only -- NOT a validated enum."
  #{"delivery" "pickup"})

(def default-confidence-floor
  "Fallback minimum advisor confidence below which a proposal escalates
  for human sign-off (see `householdrentalops.governor/confidence-floor`).
  Not a regulatory citation -- an internal operational default, the same
  discipline every sibling actor's confidence floor uses."
  0.7)
