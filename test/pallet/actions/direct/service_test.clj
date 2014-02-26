(ns pallet.actions.direct.service-test
  (:require
   [clojure.test :refer :all]
   [pallet.actions.direct.service :refer [service-impl]]
   [pallet.actions.impl :refer [service-script-path]]
   [pallet.common.logging.logutils :refer [logging-threshold-fixture]]
   [pallet.script :refer [with-script-context]]
   [pallet.stevedore :as stevedore]
   [pallet.test-utils
    :refer [make-node no-location-info
            with-bash-script-language with-ubuntu-script-template
            with-no-source-line-comments]]))

(use-fixtures
  :once
  no-location-info
  with-ubuntu-script-template
  with-bash-script-language
  with-no-source-line-comments)

(deftest service-test
  (is (script-no-comment=
       "echo start tomcat\n/etc/init.d/tomcat start\n"
       (service-impl {} "tomcat")))
  (is (script-no-comment=
       "echo stop tomcat\n/etc/init.d/tomcat stop\n"
       (service-impl {} "tomcat" :action :stop)))
  (is (script-no-comment=
       (stevedore/checked-script
        "Configure service tomcat"
        "update-rc.d tomcat defaults 20 20")
       (service-impl {} "tomcat" :action :enable)))
  (is (script-no-comment=
       (stevedore/checked-script
        "Configure service tomcat"
        "/sbin/chkconfig tomcat on --level 2345")
       (with-script-context [:centos :yum]
         (service-impl {} "tomcat" :action :enable)))))

;; (deftest with-restart-test
;;   (is (script-no-comment=

;;        (str "echo stop tomcat\n/etc/init.d/tomcat stop\n"
;;             "echo start tomcat\n/etc/init.d/tomcat start\n")
;;        (first (build-actions {}
;;                 (with-service-restart "tomcat"))))))

;; (deftest init-script-test
;;   (is (script-no-comment=
;;        (first
;;         (build-actions {:phase-context "init-script"}
;;           (remote-file
;;            (service-script-path :initd "tomcat")
;;            :action :create
;;            :content "c"
;;            :owner "root"
;;            :group "root"
;;            :mode "0755")))
;;        (first
;;         (build-actions {}
;;           (service-script "tomcat" :content "c"))))))
