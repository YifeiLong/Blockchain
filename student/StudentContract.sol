// SPDX-License-Identifier: GPL-3.0
pragma solidity >=0.4.15 <0.9.0;
pragma abicoder v2;

contract StudentContract {
    struct Student {
        uint256 id;
        string name;
        string sex;
        uint256 age;
        string dept;
    }

    address admin;
    Student[] students;
    uint256[] ids;
    uint256 count = 0;
    mapping(uint256 => uint256) indexMapping; // id映射index
    mapping(uint256 => bool) isExistMapping;

    constructor() {
        admin = msg.sender;
    }

    function insert(
        uint256 _id,
        string memory _name,
        string memory _sex,
        uint256 _age,
        string memory _dept
    ) public {
        // TODO:插入一条学生记录
        require(msg.sender == admin, "Only admin can perform this action");
        require(!isExistMapping[_id], "Student ID already exists");
        students.push(Student(_id, _name, _sex, _age, _dept));
        uint256 index = students.length - 1;
        indexMapping[_id] = index;
        isExistMapping[_id] = true;
        count ++;
        emit Insert(_id);
    }

    event Insert(uint256 id);

    function exist_by_id(uint256 _id) public view returns (bool isExist) {
        // TODO:查找系统中是否存在某个学号
        return isExistMapping[_id];
    }

    function select_count() public view returns (uint256 _count) {
        // TODO:查找系统中的学生数量
        return count;
    }

    function select_all_id() public view returns (uint256[] memory _ids) {
        // TODO:查找系统中所有的学号
        uint256[] memory id_now = new uint256[](count);
        for(uint256 i = 0; i < count; i ++){
            id_now[i] = students[i].id;
        }
        return id_now;
    }

    function select_id(uint256 _id) public view returns (Student memory) {
        // TODO:查找指定学号的学生信息
        require(isExistMapping[_id], "Student ID doesn't exist");
        return students[indexMapping[_id]];
    }

    function delete_by_id(uint256 _id) public {
        // TODO:删除指定学号的学生信息
        require(msg.sender == admin, "Only admin can perform this action");
        if(!isExistMapping[_id]){
            return;
        }

        uint256 indexToDelete = indexMapping[_id];
        uint256 lastStudentIndex = students.length - 1;

        if(indexToDelete != lastStudentIndex){
            students[indexToDelete] = students[lastStudentIndex];
            // id -> index
            indexMapping[students[indexToDelete].id] = indexToDelete;
        }
        students.pop();
        delete isExistMapping[_id];
        delete indexMapping[_id];
        count --;
    }

    function get_id_by_min_age() public view returns (uint256 minAgeId) {
        require(students.length > 0, "No student in the system");
        uint256 minAge = students[0].age;
        minAgeId = students[0].id;

        for(uint256 i = 1; i < students.length; i ++){
            if(students[i].age < minAge){
                minAge = students[i].age;
                minAgeId = students[i].id;
            }
        }
    }

    function update_dept_by_id(uint256 _id, string memory _dept) public {
        require(msg.sender == admin, "Only admin can perform this action");
        require(isExistMapping[_id], "Student ID doesn't exist");

        uint256 index = indexMapping[_id];
        students[index].dept = _dept;
        emit Update(_id, _dept);
    }
    
    event Update(uint256 id, string dept);
}
