template_java/build.sh
if [ $? -ne 0 ]; then
    exit $?
fi
trap 'kill $BGPID; exit' INT
BGPID=$!
template_java/run.sh --id 1 --hosts test0/hosts.txt --output test0/output1.txt test0/perfect-links1.config > test0/output_1.txt &
template_java/run.sh --id 2 --hosts test0/hosts.txt --output test0/output2.txt test0/perfect-links2.config > test0/output_2.txt
