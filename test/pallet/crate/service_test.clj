(ns pallet.crate.service-test
  "Provide a test suite for service supervision implementations"
  (:require
   [clojure.test :refer :all]
   [pallet.actions :refer [exec-checked-script exec-script]]
   [pallet.crate.service :refer [service-supervisor]]))

(defn service-supervisor-test
  [supervisor
   session
   {:keys [service-name] :as config}
   {:keys [process-name] :as supervisor-options}]
  (let [process-name (or process-name service-name)]
    (service-supervisor
     session
     supervisor config
     (assoc supervisor-options :action :enable))
    (testing "can start"
      (service-supervisor
       session
       supervisor config
       (assoc supervisor-options :action :start :if-stopped true))
      (exec-checked-script
       session
       "check process is up"
       ("pgrep" -f (quoted ~(name process-name)))))
    (testing "can restart"
      (let [pid (exec-script
                 session
                 ("pgrep" -f (quoted ~(name process-name))))]
        (service-supervisor
         session
         supervisor config
         (assoc supervisor-options :action :restart))
        (let [pid2 (exec-checked-script
                    session
                    "check process is up after restart"
                    ("pgrep" -f (quoted ~(name process-name))))]
          (assert (not= (:out pid) (:out pid2))
                  (str "old pid: " (:out pid)
                       " new pid: " (:out pid2))))))
    (testing "start when started is ok"
      (service-supervisor
       session
       supervisor config (assoc supervisor-options :action :start)))
    (testing "can stop"
      (service-supervisor
       session
       supervisor config
       (assoc supervisor-options :action :stop))
      (exec-checked-script
       session
       "check process is down"
       (not ("pgrep" -f (quoted ~(name process-name))))))))
