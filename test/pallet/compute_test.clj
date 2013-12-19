(ns pallet.compute-test
  (:require
   [clojure.test :refer :all]
   [pallet.compute :refer :all]))

(deftest packager-test
  (is (= :apt (packager-for-os :ubuntu nil)))
  (is (= :yum (packager-for-os :centos nil)))
  (is (= :portage (packager-for-os :gentoo nil)))
  (is (= :pkgin (packager-for-os :smartos nil))))

(deftest base-distribution-test
  (is (= :debian (base-distribution {:os-family :ubuntu})))
  (is (= :rh (base-distribution {:os-family :centos})))
  (is (= :gentoo (base-distribution {:os-family :gentoo})))
  (is (= :arch (base-distribution {:os-family :arch})))
  (is (= :suse (base-distribution {:os-family :suse})))
  (is (= :system-v (base-distribution {:os-family :smartos}))))


(defmulti-os testos [session])
(defmethod testos :linux [session] :linux)
(defmethod testos :debian [session] :debian)
(defmethod testos :rh-base [session] :rh-base)
(defmethod testos :system-v [session] :system-v)

(deftest defmulti-os-test
  (is (= :linux (testos {:server {:image {:os-family :arch}}})))
  (is (= :rh-base (testos {:server {:image {:os-family :centos}}})))
  (is (= :debian (testos {:server {:image {:os-family :debian}}})))
  (is (= :system-v (testos {:server {:image {:os-family :smartos}}})))
  (is (thrown? clojure.lang.ExceptionInfo
               (testos {:server {:image {:os-family :unspecified}}}))))
