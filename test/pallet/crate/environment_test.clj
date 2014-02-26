(ns pallet.crate.environment-test
  (:require
   [clojure.test :refer :all]
   [pallet.action-options :refer [with-action-options]]
   [pallet.actions :refer [file]]
   [pallet.build-actions :refer [build-actions build-script]]
   [pallet.common.logging.logutils :refer [logging-threshold-fixture]]
   [pallet.compute.node-list :refer [make-localhost-node]]
   [pallet.crate.environment :refer [system-environment
                                     system-environment-file]]
   [pallet.group :refer [lift group-spec phase-errors]]
   [pallet.plan :refer [plan-fn]]
   [pallet.script :refer [with-script-context]]
   [pallet.stevedore :refer [with-script-language]]
   [pallet.test-utils :refer [make-node test-username]]
   [pallet.user :refer [*admin-user*]]
   [pallet.utils :refer [tmpfile with-temporary]]))

(use-fixtures :once (logging-threshold-fixture))

(defn- local-test-user
  []
  (assoc *admin-user* :username (test-username) :no-sudo true))

(deftest system-environment-file-test
  (with-script-language :pallet.stevedore.bash/bash
    (is (= ["/etc/environment" true]
           (with-script-context [:ubuntu]
             (system-environment-file
              {:server {:node (make-node "n1" :os-family :ubuntu)}}
              "xx" {}))))
    (is (= ["/etc/profile.d/xx.sh" false]
           (with-script-context [:centos]
             (system-environment-file
              {:server {:node (make-node "n1" :os-family :centos)}}
              "xx" {}))))))


(deftest service-test
  (is
   (script-no-comment=
    "echo 'system-environment: Add testenv environment to /etc/environment...';\n{\nif ! ( [ -e /etc/environment ] ); then\n{ cat > /etc/environment <<EOFpallet\n# environment file created by pallet\n\nEOFpallet\n }\nfi\npallet_set_env() {\nk=$1\nv=$2\ns=$3\nif ! ( grep \"${s}\" /etc/environment 2>&- ); then\nsed -i -e \"/$${k}=/ d\" /etc/environment && sed -i -e \"$ a \\\\\n${s}\" /etc/environment || exit 1\nfi\n} && vv=\"1\"\npallet_set_env \"A\" \"${vv}\" \"A=\\\"${vv}\\\"\" && vv=\"b\"\npallet_set_env \"B\" \"${vv}\" \"B=\\\"${vv}\\\"\"\n } || { echo '#> system-environment: Add testenv environment to /etc/environment : FAIL'; exit 1;} >&2 \necho '#> system-environment: Add testenv environment to /etc/environment : SUCCESS'\n"
    (build-script [session {}]
      (system-environment session "testenv" {"A" 1 :B "b"}))))
  (is
   (script-no-comment=
    "echo 'system-environment: Add testenv environment to /etc/environment...';\n{\nif ! ( [ -e /etc/environment ] ); then\n{ cat > /etc/environment <<EOFpallet\n# environment file created by pallet\n\nEOFpallet\n }\nfi\npallet_set_env() {\nk=$1\nv=$2\ns=$3\nif ! ( grep \"${s}\" /etc/environment 2>&- ); then\nsed -i -e \"/$${k}=/ d\" /etc/environment && sed -i -e \"$ a \\\\\n${s}\" /etc/environment || exit 1\nfi\n} && vv='1'\npallet_set_env \"A\" \"${vv}\" \"A=\\\"${vv}\\\"\" && vv='b'\npallet_set_env \"B\" \"${vv}\" \"B=\\\"${vv}\\\"\"\n } || { echo '#> system-environment: Add testenv environment to /etc/environment : FAIL'; exit 1;} >&2 \necho '#> system-environment: Add testenv environment to /etc/environment : SUCCESS'\n\n"
    (build-script [session {}]
      (system-environment session "testenv" {"A" 1 :B "b"} :literal true)))))

(deftest service-local-test
  (with-temporary [env-file (tmpfile)]
    (.delete env-file)
    (let [user (local-test-user)
          node (make-localhost-node)
          a (atom nil)
          path (.getAbsolutePath env-file)
          get-sysenv (fn []
                       (reset! a (system-environment-file
                                  "pallet-testenv" {:path path})))
          local (group-spec "local")]
      (testing "insert"
        (let [result (lift {local node}
                           :user user
                           :phase (plan-fn [session]
                                    (with-action-options
                                      session
                                      {:script-trace true}
                                      (system-environment
                                       session
                                       "pallet-testenv"
                                       {"a" "$xxxx"}
                                       :literal true
                                       :path path))
                                    (get-sysenv)))
              [path shared] @a]
          (is (nil? (phase-errors result)))
          (is @a)
          (is path)
          (is shared)
          (is (= (.getAbsolutePath env-file) path))
          (is (slurp path))
          (if shared
            (do
              (is (re-find #"a=\"\$xxxx\"" (slurp path))))
            (= "a=\"$xxxx\"" (slurp path)))
          (.startsWith (slurp path) "# ")))
      (testing "replace"
        (let [result (lift {local node} :user user
                           :phase (plan-fn [session]
                                    (system-environment
                                     session
                                     "pallet-testenv"
                                     {"a" "$xxyy"}
                                     :literal true
                                     :path path)
                                    (get-sysenv)))
              [path shared] @a]
          (is (nil? (phase-errors result)))
          (is path)
          (is shared)
          (is (= (.getAbsolutePath env-file) path))
          (if shared
            (is (re-find #"a=\"\$xxyy\"" (slurp path)))
            (is (= "a=\"$xxyy\"" (slurp path)))))))))
