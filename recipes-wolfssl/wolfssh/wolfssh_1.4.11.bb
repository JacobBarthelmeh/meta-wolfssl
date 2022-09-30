SUMMARY = "wolfSSH Lightweight SSH Library"
DESCRIPTION = "wolfSSH is a lightweight SSHv2 library written in ANSI C and \
               targeted for embedded, RTOS, and resource-constrained \
               environments. wolfSSH supports client and server sides, and \
               includes support for SCP and SFTP."
HOMEPAGE = "https://www.wolfssl.com/products/wolfssh"
BUGTRACKER = "https://github.com/wolfssl/wolfssh/issues"
SECTION = "libs"
LICENSE = "GPLv3"
LIC_FILES_CHKSUM = "file://LICENSING;md5=2c2d0ee3db6ceba278dd43212ed03733"

DEPENDS += "wolfssl libxcrypt"

SRC_URI = "git://github.com/wolfssl/wolfssh.git;protocol=https;tag=v${PV}-stable"

SRC_URI_append    = " file://wolfsshd@.service \
                      file://wolfssh.socket \
                      file://sshd_config "

S = "${WORKDIR}/git"

inherit autotools pkgconfig

EXTRA_OECONF = "--with-wolfssl=${COMPONENTS_DIR}/${PACKAGE_ARCH}/wolfssl/usr --enable-sshd --enable-sftp --bindir=/usr/sbin"

# add options for a wolfssh-sshd package
inherit systemd

SYSTEMD_PACKAGES           = "${PN}"
SYSTEMD_SERVICE_${PN} = "wolfssh.socket"

# USERADD_PACKAGES      =  "${PN}-sshd"
# PACKAGES             += "${PN}-sshd"
FILES_${PN}     += "${system_uintdir}/system/wolfsshd@.service"
RPROVIDES_${PN}  = "sshd"
RCONFLICTS_${PN} = "openssh dropbear openssh-sshd"
CONFILES_${PN}   = "${sysconfdir}/ssh/sshd_config"

do_configure_prepend() {
    (cd ${S}; ./autogen.sh; cd -)
}

do_install_append() {
    install -d ${D}/${systemd_unitdir}/system
    install -m 0644 ${WORKDIR}/sshd_config ${B}/
    install -m 0644 ${WORKDIR}/wolfssh.socket ${D}/${systemd_unitdir}/system
}
