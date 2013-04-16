(ns pallet.script.lib-test
  (:use pallet.script.lib)
  (:use [pallet.stevedore :only [map-to-arg-string fragment script]]
        clojure.test)
  (:require
   [pallet.script :as script]
   [pallet.test-utils :as test-utils]))

(use-fixtures
 :once
 test-utils/with-ubuntu-script-template
 test-utils/with-bash-script-language)


(deftest exit-test
  (is (script-no-comment=
       "exit 1"
       (script (~exit 1)))))

(deftest rm-test
  (is (script-no-comment=
       "rm --force file1"
       (script (~rm "file1" :force true)))))

(deftest mv-test
  (is (script-no-comment=
       "mv --backup=\"numbered\" file1 file2"
       (script (~mv "file1" "file2" :backup :numbered)))))

(deftest ln-test
  (is (script-no-comment=
       "ln -s file1 file2"
       (script (~ln "file1" "file2" :symbolic true)))))

(deftest chown-test
  (is (script-no-comment=
       "chown user1 file1"
       (script (~chown "user1" "file1")))))

(deftest chgrp-test
  (is (script-no-comment=
       "chgrp group1 file1"
       (script (~chgrp "group1" "file1")))))

(deftest chmod-test
  (is (script-no-comment=
       "chmod 0666 file1"
       (script (~chmod "0666" "file1")))))

(deftest tmpdir-test
  (is (script-no-comment= "${TMPDIR-/tmp}"
                          (script (~tmp-dir)))))

(deftest normalise-md5-test
  (is (script-no-comment=
       (script
        (if ("egrep" "'^[a-fA-F0-9]+$'" abc.md5)
          (println
           (quoted (str "  " @(pipe ("basename" abc.md5)
                                    ("sed" -e "s/.md5//"))))
           ">>" abc.md5))
        ("sed" -i -e (quoted "s_/.*/\\(..*\\)_\\1_") "abc.md5"))
       (script (~normalise-md5 abc.md5)))))

(deftest md5sum-verify-test
  (is (script-no-comment=
       (script
        ("(" (chain-and
              ("cd" @("dirname" abc.md5))
              ("md5sum"
               ~(map-to-arg-string {:quiet true :check true})
               @("basename" abc.md5))) ")"))
       (script (~md5sum-verify abc.md5)))))

(deftest heredoc-test
  (is (script-no-comment=
       "{ cat > somepath <<EOFpallet\nsomecontent\nEOFpallet\n }"
       (script (~heredoc "somepath" "somecontent" {})))))

(deftest heredoc-literal-test
  (is (script-no-comment=
       "{ cat > somepath <<'EOFpallet'\nsomecontent\nEOFpallet\n }"
       (script (~heredoc "somepath" "somecontent" {:literal true})))))

(deftest sed-file-test
  (testing "explicit separator"
    (is (script-no-comment=
         "sed -i -e \"s|a|b|\" path"
         (script (~sed-file "path" {"a" "b"} {:seperator "|"})))))
  (testing "single quotings"
    (is (script-no-comment=
         "sed -i -e 's/a/b/' path"
         (script (~sed-file "path" {"a" "b"} {:quote-with "'"})))))
  (testing "computed separator"
    (is (script-no-comment=
         "sed -i -e \"s/a/b/\" path"
         (script (~sed-file "path" {"a" "b"} {}))))
    (is (script-no-comment=
         "sed -i -e \"s_a/_b_\" path"
         (script (~sed-file "path" {"a/" "b"} {}))))
    (is (script-no-comment=
         "sed -i -e \"s_a_b/_\" path"
         (script (~sed-file "path" {"a" "b/"} {}))))
    (is (script-no-comment=
         "sed -i -e \"s*/_|:%!@*b*\" path"
         (script (~sed-file "path" {"/_|:%!@" "b"} {})))))
  (testing "restrictions"
    (is (script-no-comment=
         "sed -i -e \"1 s/a/b/\" path"
         (script (~sed-file "path" {"a" "b"} {:restriction "1"}))))
    (is (script-no-comment=
         "sed -i -e \"/a/ s/a/b/\" path"
         (script (~sed-file "path" {"a" "b"} {:restriction "/a/"})))))
  (testing "other commands"
    (is (script-no-comment=
         "sed -i -e \"1 a\" path"
         (script (~sed-file "path" "a" {:restriction "1"}))))))

(deftest make-temp-file-test
  (is (script-no-comment=
       "$(mktemp \"prefixXXXXX\")"
       (script (~make-temp-file "prefix")))))

(deftest download-file-test
  (is (script (~download-file "http://server.com/" "/path")))
  (is (script-no-comment=
       "if\nhash curl 2>&-; then curl -o \"/path\" --retry 5 --silent --show-error --fail --location --proxy localhost:3812 \"http://server.com/\";else\nif\nhash wget 2>&-; then wget -O \"/path\" --tries 5 --no-verbose --progress=dot:mega -e \"http_proxy = http://localhost:3812\" -e \"ftp_proxy = http://localhost:3812\" \"http://server.com/\";else\necho No download utility available\nexit 1\nfi\nfi"
       (script
        (~download-file
         "http://server.com/" "/path" :proxy "http://localhost:3812"))))
  (is (script-no-comment=
       "if\nhash curl 2>&-; then curl -o \"/path\" --retry 5 --silent --show-error --fail --location --proxy localhost:3812 --insecure \"http://server.com/\";else\nif\nhash wget 2>&-; then wget -O \"/path\" --tries 5 --no-verbose --progress=dot:mega -e \"http_proxy = http://localhost:3812\" -e \"ftp_proxy = http://localhost:3812\" --no-check-certificate \"http://server.com/\";else\necho No download utility available\nexit 1\nfi\nfi"
       (script
        (~download-file
         "http://server.com/" "/path" :proxy "http://localhost:3812"
         :insecure true)))
      ":insecure should disable ssl checks"))

(deftest download-request-test
  (is (script-no-comment=
       "curl -o \"p\" --retry 3 --silent --show-error --fail --location -H \"n: v\" \"http://server.com\""
       (let [request {:headers {"n" "v"}
                      :endpoint (java.net.URI. "http://server.com")}]
         (script (~download-request "p" ~request))))))

(deftest mkdir-test
  (is (script-no-comment=
       "mkdir -p dir"
       (script (~mkdir "dir" :path ~true)))))

;;; user management

(deftest create-user-test
  (is (script-no-comment=
       "/usr/sbin/useradd --create-home user1"
       (script (~create-user "user1"  ~{:create-home true}))))
  (is (script-no-comment=
       "/usr/sbin/useradd --system user1"
       (script (~create-user "user1"  ~{:system true}))))
  (testing "system on rh"
    (script/with-script-context [:centos]
      (is (script-no-comment=
           "/usr/sbin/useradd -r user1"
           (script (~create-user "user1"  ~{:system true}))))))
  (testing "system on smartos"
    (script/with-script-context [:smartos]
      (is (= (str "/usr/sbin/useradd testing && \n" expect-password-test "\n")
	     (script (~create-user "testing" ~{:password "pass"})))))
    (script/with-script-context [:smartos]
     (is (= "/usr/sbin/useradd testing\n"
	     (script (~create-user "testing" ~{:system true} )))))))

(deftest modify-user-test
  (is (script-no-comment=
       "/usr/sbin/usermod --home \"/home2/user1\" --shell \"/bin/bash\" user1"
       (script
        (~modify-user
         "user1"  ~{:home "/home2/user1" :shell "/bin/bash"}))))
  (script/with-script-context [:smartos]
    (is (= "/usr/sbin/usermod -s \"/bin/bash\" user1\n"
	   (script
	    (~modify-user
	     "user1" ~{:shell "/bin/bash"}))))))


;;; package management

(deftest update-package-list-test
  (is (script-no-comment=
       "aptitude update -q=2 -y || \\\ntrue"
       (script/with-script-context [:aptitude]
         (script (~update-package-list)))))
  (is (script-no-comment=
       "yum makecache -q"
       (script/with-script-context [:yum]
         (script (~update-package-list)))))
  (is (script-no-comment=
       "zypper refresh"
       (script/with-script-context [:zypper]
         (script (~update-package-list)))))
  (is (script-no-comment=
       "pacman -Sy --noconfirm --noprogressbar"
       (script/with-script-context [:pacman]
         (script (~update-package-list))))))

(deftest upgrade-all-packages-test
  (is (script-no-comment=
       "aptitude upgrade -q -y"
       (script/with-script-context [:aptitude]
         (script (~upgrade-all-packages)))))
  (is (script-no-comment=
       "yum update -y -q"
       (script/with-script-context [:yum]
         (script (~upgrade-all-packages)))))
  (is (script-no-comment=
       "zypper update -y"
       (script/with-script-context [:zypper]
         (script (~upgrade-all-packages)))))
  (is (script-no-comment=
       "pacman -Su --noconfirm --noprogressbar"
       (script/with-script-context [:pacman]
         (script (~upgrade-all-packages))))))

(deftest install-package-test
  (is (script-no-comment=
       "aptitude install -q -y java && aptitude show java"
       (script/with-script-context [:aptitude]
         (script (~install-package "java")))))
  (is (script-no-comment=
       "yum install -y -q java"
       (script/with-script-context [:yum]
         (script (~install-package "java"))))))

(deftest list-installed-packages-test
  (is (script-no-comment=
       "aptitude search \"~i\""
       (script/with-script-context [:aptitude]
         (script (~list-installed-packages)))))
  (is (script-no-comment=
       "yum list installed"
       (script/with-script-context [:yum]
         (script (~list-installed-packages))))))




;;; test hostinfo

(deftest dnsdomainname-test
  (is (script-no-comment=
       "$(dnsdomainname)"
       (script (~dnsdomainname)))))

(deftest dnsdomainname-test
  (is (script-no-comment=
       "$(hostname --fqdn)"
       (script (~hostname :fqdn true)))))

(deftest nameservers-test
  (is (script-no-comment=
       "$(grep nameserver /etc/resolv.conf | cut -f2)"
       (script (~nameservers)))))

;;; test filesystem paths
(defmacro mktest
  [os-family f path]
  `(is (= ~path
          (script/with-script-context [~os-family]
            (fragment
             ~(list f))))))

(deftest etc-default-test
  (mktest :ubuntu etc-default "/etc/default")
  (mktest :debian etc-default "/etc/default")
  (mktest :centos etc-default "/etc/sysconfig")
  (mktest :fedora etc-default "/etc/sysconfig")
  (mktest :os-x etc-default "/etc/defaults")
  (mktest :solaris etc-default "/etc/defaults")
  (mktest :smartos etc-default "/etc/defaults"))

(deftest config-root-test
  (mktest :ubuntu config-root "/etc"))

(deftest file-test
  (is (=  "/etc/riemann" (fragment (file (config-root) "riemann"))))
  (let [a "/etc" b "riemann"]
    (is (=  "/etc/riemann" (fragment (file ~a ~b))))))
